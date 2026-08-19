package com.ms.petopia.api.application.service;

import com.ms.petopia.api.application.domain.*;
import com.ms.petopia.api.application.dto.request.*;
import com.ms.petopia.api.application.dto.response.*;
import com.ms.petopia.api.application.mapper.ApplicationMapper;
import com.ms.petopia.api.booth.domain.Booth;
import com.ms.petopia.api.booth.mapper.BoothMapper;
import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.api.fair.service.BoothSlotService;
import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.recruitnotice.domain.FairStatusInfo;
import com.ms.petopia.api.recruitnotice.domain.RecruitNotice;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.api.refund.dto.RefundReason;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RequestedByDomain;
import com.ms.petopia.api.refund.service.RefundService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationMapper applicationMapper;
    private final BusinessMapper businessMapper;
    private final RecruitNoticeMapper recruitNoticeMapper;
    private final StorageService storageService;
    private final RefundService refundService;
    private final NotificationService notificationService;
    private final BoothMapper boothMapper;
    private final BoothSlotService boothSlotService;
    private final FairAdminAccessGuard fairAdminAccessGuard;

    // 부스 슬롯 목록 + 잠금 상태 조회
    public List<BoothSlotLockStatusResponse> getBoothSlots(Long fairId) {

        if(!applicationMapper.existsFair(fairId)) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }

        return applicationMapper.selectBoothSlotsWithLockStatus(fairId);

    }

    // 참가 신청서 제출
    @Transactional
    public ApplicationResponse submitApplication(Long ownerId, Long fairId, ApplicationSubmitRequest request) {

        // 검증 통과 시 가격 스냅샷용 슬롯 맵 리턴
        Map<Long, BoothSlotLockStatusResponse> slotsById = validateSubmission(ownerId, fairId, request);

        // application -> application_form -> application_slot*N 순서로 저장
        Application application = saveApplication(fairId, request);
        ApplicationForm form = saveApplicationForm(application.getApplicationId(), request);
        saveApplicationSlots(application.getApplicationId(), request.getBoothSlotIds(), slotsById);

        // 슬롯 잠그기
        lockSlots(application.getApplicationId());

        // 재조회 후 응답 조립
        Application saved = applicationMapper.selectById(application.getApplicationId());

        return ApplicationResponse.from(saved, form, request.getBoothSlotIds());

    }

    /*
     * 신청서 제출 검증: 약관동의 -> 사업자 소유확인 -> 행사존재 -> 중복신청 -> 슬롯 존재/잠금.
     * 통과하면 가격 스냅샷용 슬롯 맵을 리턴한다.
     */
    private Map<Long, BoothSlotLockStatusResponse> validateSubmission(Long ownerId, Long fairId,
                                                                      ApplicationSubmitRequest request) {

        // 1) 약관 동의 확인
        if(request.getAgreedTerms() == null || !request.getAgreedTerms()) {
            throw new CommonException(ErrorCode.TERMS_NOT_AGREED);
        }

        // 2) 사업자 소유 확인
        Business business = businessMapper.selectById(request.getBusinessId());

        if(business == null) {
            throw new CommonException(ErrorCode.BUSINESS_NOT_FOUND);
        }

        if(!business.getOwnerId().equals(ownerId)) {
            throw new CommonException(ErrorCode.ACCESS_DENIED, "본인 소유의 사업자만 신청할 수 있습니다.");
        }

        // 승인된 사업자만 신청 가능 - 프론트 필터링은 UX일 뿐, 서버가 직접 막아야 함
        if (business.getApprovalStatus() != Business.ApprovalStatus.APPROVED) {
            throw new CommonException(ErrorCode.BUSINESS_APPROVAL_REQUIRED);
        }

        // 3) 행사 존재 확인 + 3-1) 모집 마감 여부 확인 (fairStatus 한 번 조회해서 같이 처리)
        FairStatusInfo fairStatus = recruitNoticeMapper.selectFairStatusByFairId(fairId);

        if (fairStatus == null) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }

        if (isRecruitClosed(fairId, fairStatus)) {
            throw new CommonException(ErrorCode.RECRUIT_CLOSED);
        }

        // 같은 사업자의 동시 신청 직렬화를 위해 business 행 자체를 잠금 (이미 2번 단계에서 존재 확인된 행)
        businessMapper.lockBusinessForApplication(request.getBusinessId());

        // 4) 중복 신청 확인
        if(applicationMapper.existsActiveApplication(request.getBusinessId(), fairId)) {
            throw new CommonException(ErrorCode.APPLICATION_DUPLICATE_ACTIVE);
        }

        // 5) 슬롯 중복 선택 확인 + 존재/잠금 확인 (GET에서 쓴 쿼리 재사용)
        // 슬롯을 최소 1개 선택했는지 확인 (컨트롤러의 @NotEmpty가 항상 걸러주지만, 서비스 단독 호출 대비 방어)
        if (request.getBoothSlotIds() == null || request.getBoothSlotIds().isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "부스 슬롯을 최소 1개 선택해야 합니다.");
        }

        // 같은 슬롯을 중복으로 선택했는지 확인
        if (new HashSet<>(request.getBoothSlotIds()).size() != request.getBoothSlotIds().size()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "같은 부스 슬롯을 중복으로 선택할 수 없습니다.");
        }

        Map<Long, BoothSlotLockStatusResponse> slotsById = applicationMapper.selectBoothSlotsWithLockStatus(fairId)
                .stream()
                .collect(Collectors.toMap(BoothSlotLockStatusResponse::getBoothSlotsId, s -> s));

        for(Long boothSlotId : request.getBoothSlotIds()) {

            BoothSlotLockStatusResponse slot = slotsById.get(boothSlotId);

            // 이 행사에 존재하지 않는 슬롯인지 확인
            if(slot == null ) {
                throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "이 행사에 존재하지 않는 부스 슬롯이 포함되어 있습니다.");
            }

            // 다른 활성 신청이 이미 선점했는지 확인
            if(slot.getLocked()) {
                throw new CommonException(ErrorCode.BOOTH_SLOT_ALREADY_LOCKED);
            }

        }

        /*
         * 부스 슬롯별 애플리케이션 레벨 락. booth_slots/application_slot 테이블은 전혀 건드리지
         * 않고, boothSlotId 문자열 이름에만 락을 걸어 동시 신청을 직렬화한다(코드래빗 리뷰 반영 —
         * application_slot에 매칭 행이 없는 신규 슬롯은 FOR UPDATE로 잠글 대상 자체가 없었음).
         * 데드락 방지를 위해 슬롯 ID는 정렬된 순서로 잠근다.
         */
        List<Long> sortedSlotIds = request.getBoothSlotIds().stream().sorted().toList();

        for (Long boothSlotId : sortedSlotIds) {

            Integer locked = applicationMapper.acquireBoothSlotLock(boothSlotId);

            if (locked == null || locked != 1) {
                throw new CommonException(ErrorCode.BOOTH_SLOT_ALREADY_LOCKED);
            }

            // 락 하나 잡을 때마다 즉시 해제 예약 — 이후 슬롯에서 실패해도 이미 잡은 락은 안전하게 풀림
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCompletion(int status) {
                    applicationMapper.releaseBoothSlotLock(boothSlotId);
                }

            });

        }

        // 락 확보 후, 실제로 활성 신청에 걸린 슬롯이 있는지 확인
        List<Long> lockedNow = applicationMapper.selectLockedBoothSlotIds(sortedSlotIds);

        if (!lockedNow.isEmpty()) {
            throw new CommonException(ErrorCode.BOOTH_SLOT_ALREADY_LOCKED);
        }

        return slotsById;

    }

    // 모집 공고 마감 판정 (RecruitNoticeService.isClosed()와 동일 기준: 마감일 지남/행사취소/행사종료)
    private boolean isRecruitClosed(Long fairId, FairStatusInfo fairStatus) {

        RecruitNotice notice = recruitNoticeMapper.selectByFairId(fairId);

        boolean deadlinePassed = notice != null &&
                !LocalDateTime.now().isBefore(notice.getRecruitDeadline());
        boolean fairCanceled = fairStatus.getCanceledAt() != null;
        boolean fairEnded = "ENDED".equals(fairStatus.getStatus());

        return deadlinePassed || fairCanceled || fairEnded;

    }

    // application 저장 (PENDING_REVIEW로 생성)
    private Application saveApplication(Long fairId, ApplicationSubmitRequest request) {

        Application application = Application.builder()
                .businessId(request.getBusinessId())
                .fairId(fairId)
                .build();

        applicationMapper.insertApplication(application);

        return application;

    }

    // application_form 저장 (application과 1:1, 제출 당시 내용은 이후 수정하지 않음)
    private ApplicationForm saveApplicationForm(Long applicationId, ApplicationSubmitRequest request) {

        ApplicationForm form = ApplicationForm.builder()
                .applicationId(applicationId)
                .purpose(request.getPurpose())
                .itemsDesc(request.getItemsDesc())
                .managerName(request.getManagerName())
                .managerPhone(request.getManagerPhone())
                .managerEmail(request.getManagerEmail())
                .agreedTerms(request.getAgreedTerms())
                .attachmentUrl(resolveAttachmentUrl(request.getAttachmentObjectKey()))
                .build();

        applicationMapper.insertApplicationForm(form);

        return form;

    }

    // application_slot 저장 (선택한 슬롯 개수만큼 반복 호출, 가격은 신청 시점 스냅샷)
    private void saveApplicationSlots(Long applicationId, List<Long> boothSlotIds,
                                      Map<Long, BoothSlotLockStatusResponse> slotsById) {

        for(Long boothSlotId : boothSlotIds) {

            ApplicationSlot slot = ApplicationSlot.builder()
                    .applicationId(applicationId)
                    .boothSlotId(boothSlotId)
                    .priceAtSelection(slotsById.get(boothSlotId).getPrice())
                    .build();

            applicationMapper.insertApplicationSlot(slot);

        }

    }

    /*
     * presigned 업로드로 받은 임시 객체 키를 확정(tmp → uploads)하고 공개 URL로 바꾼다.
     * 키가 없으면(첨부파일을 안 넣었으면) null을 그대로 반환한다.
     */
    private String resolveAttachmentUrl(String temporaryObjectKey) {

        if (temporaryObjectKey == null || temporaryObjectKey.isBlank()) {
            return null;
        }

        String confirmedKey = storageService.confirm(temporaryObjectKey, UploadPolicy.DOCUMENT);

        return storageService.toPublicUrl(confirmedKey);

    }

    // 내 신청 현황 목록 조회 (businessId는 선택적 필터)
    public List<ApplicationSummaryResponse> getMyApplications(Long ownerId, Long businessId) {

        return applicationMapper.selectMyApplications(ownerId, businessId);

    }

    // 신청 상세 조회 (사업자 본인 또는 담당 행사 관리자 조회 가능)
    public ApplicationDetailResponse getApplicationDetail(Long userId, Long applicationId) {

        ApplicationDetailResponse detail = applicationMapper.selectApplicationDetail(applicationId);

        if(detail == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 본인 소유 사업자의 신청인지 확인
        Business business = businessMapper.selectById(detail.getBusinessId());
        boolean isOwner = business != null && business.getOwnerId().equals(userId);

        // 본인 소유가 아니면, 이 신청이 속한 행사의 담당자인지 확인
        boolean isFairAdmin = false;

        if(!isOwner) {

            Long fairAdminUserId = recruitNoticeMapper.selectAdminUserIdByFairId(detail.getFairId());
            isFairAdmin = fairAdminUserId != null && fairAdminUserId.equals(userId);

        }

        if(!isOwner && !isFairAdmin) {
            throw new CommonException(ErrorCode.ACCESS_DENIED, "본인 소유의 신청이거나 담당 행사여야 조회할 수 있습니다.");
        }

        // 선택 슬롯 목록 채우기
        detail.setSlots(applicationMapper.selectApplicationSlotDetails(applicationId));

        // 취소 요청 가능 여부는 사업자 본인 관점에서만 의미 있음 (담당자는 취소 요청 주체가 아님)
        if(isOwner) {
            detail.setCancelable(isCancelable(detail));
        }

        return detail;

    }

    /*
     * 참가 신청서 내용 수정(본인 소유만, 심사 대기 상태에서만). 부스 슬롯은 수정 범위 밖 —
     * 슬롯을 바꾸려면 취소 요청 후 재신청해야 한다(잠금/가격스냅샷 로직을 여기서 다시 타지 않기 위함).
     */
    @Transactional
    public ApplicationDetailResponse updateApplication(Long ownerId, Long applicationId,
                                                       ApplicationUpdateRequest request) {

        // 신청 존재 확인
        Application application = applicationMapper.selectById(applicationId);

        if(application == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 본인 소유 사업자의 신청인지 확인
        Business business = businessMapper.selectById(application.getBusinessId());

        if(business == null || !business.getOwnerId().equals(ownerId)) {
            throw new CommonException(ErrorCode.ACCESS_DENIED, "본인 소유의 신청만 수정할 수 있습니다.");
        }

        // 심사 대기 상태에서만 수정 가능 (이미 승인/결제/확정된 건은 수정 불가)
        if(application.getStatus() != Application.Status.PENDING_REVIEW) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_PENDING_REVIEW, "심사 대기 중인 신청서만 수정할 수 있습니다.");
        }

        /*
         * 새 첨부파일이 안 왔으면(제출 필드만 수정하는 등 첨부는 안 건드린 경우) 기존 첨부파일을
         * 그대로 유지한다 - 안 그러면 attachment_url이 매번 null로 덮어써져서 첨부파일이 사라진다.
         */
        ApplicationDetailResponse existing = applicationMapper.selectApplicationDetail(applicationId);

        String attachmentUrl = (request.getAttachmentObjectKey() != null && !request.getAttachmentObjectKey().isBlank())
                ? resolveAttachmentUrl(request.getAttachmentObjectKey())
                : (existing != null ? existing.getAttachmentUrl() : null);

        ApplicationForm form = ApplicationForm.builder()
                .applicationId(applicationId)
                .purpose(request.getPurpose())
                .itemsDesc(request.getItemsDesc())
                .managerName(request.getManagerName())
                .managerPhone(request.getManagerPhone())
                .managerEmail(request.getManagerEmail())
                .attachmentUrl(attachmentUrl)
                .build();

        int updatedRows = applicationMapper.updateApplicationForm(form);

        if (updatedRows == 0) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_PENDING_REVIEW, "심사 대기 중인 신청서만 수정할 수 있습니다.");
        }

        // 수정된 최신 상태로 재조회해서 응답 (기존 상세 조회 로직 재사용)
        return getApplicationDetail(ownerId, applicationId);

    }

    // 취소 요청 가능 여부 계산 (프론트 버튼 활성화 판단용)
    private boolean isCancelable(ApplicationDetailResponse detail) {

        // 승인/확정된 신청서만 취소 대상 (심사 대기·반려·이미 취소된 건 취소할 게 없음)
        if (!Application.Status.PAYMENT_PENDING.name().equals(detail.getStatus())
                && !Application.Status.CONFIRMED.name().equals(detail.getStatus())) {
            return false;
        }

        // 이미 처리 대기 중인 취소 요청이 있으면 중복 요청 방지 위해 버튼 비활성화
        if (ApplicationCancelRequest.Status.REQUESTED.name().equals(detail.getCancelRequestStatus())) {
            return false;
        }

        // 행사 시작 7일 전 마감 기한 확인
        LocalDate operationStartDate = applicationMapper.selectOperationStartDateByFairId(detail.getFairId());

        /*
         * operationStartDate가 null(운영 시작일 미정)이면 제한할 근거가 없으므로 통과,
         * 아니면 "오늘이 (행사 시작일 - 7일)보다 이후"가 아닐 때만 취소 가능
         */
        return operationStartDate == null || !LocalDate.now().isAfter(operationStartDate.minusDays(7));

    }

    // 담당 행사의 신청 목록 조회 (행사 담당자용)
    public List<ApplicationReviewSummaryResponse> getApplicationsForFair(Long fairId, String status) {

        // 이 행사의 담당자가 요청자 본인인지 확인
        fairAdminAccessGuard.checkAssigned(fairId);

        return applicationMapper.selectApplicationsByFair(fairId, status);

    }

    // 담당 행사의 취소 요청 목록 조회 (행사 담당자용)
    public List<ApplicationCancelRequestSummaryResponse> getCancelRequestsForFair(Long fairId, String status) {

        // 이 행사의 담당자가 요청자 본인인지 확인
        fairAdminAccessGuard.checkAssigned(fairId);

        return applicationMapper.selectCancelRequestsByFair(fairId, status);

    }

    // 참가 신청서 승인 (행사 담당자용)
    @Transactional
    public ApplicationReviewResultResponse approveApplication(Long applicationId, ApplicationApproveRequest request) {

        // 신청 존재 확인
        Application application = applicationMapper.selectById(applicationId);

        if(application == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 이 신청이 속한 행사의 담당자가 요청자 본인인지 확인
        fairAdminAccessGuard.checkAssigned(application.getFairId());

        // 심사 대기 상태인지 확인 (이미 승인/반려된 신청서는 재처리 불가)
        if(application.getStatus() != Application.Status.PENDING_REVIEW) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_PENDING_REVIEW);
        }

        // 최종 가격 확정 — 담당자가 직접 finalPrice를 넣으면 그 값을, 안 넣으면(null) 슬롯 가격 합계를 사용
        Long finalPrice = (request != null && request.getFinalPrice() != null)
                ? request.getFinalPrice()
                : applicationMapper.sumSlotPricesByApplicationId(applicationId);

        // 승인 처리: PAYMENT_PENDING 전환 + 결제 마감일(3일 뒤) 확정
        LocalDateTime reviewedAt = LocalDateTime.now();
        LocalDateTime paymentDueAt = reviewedAt.plusDays(3);

        // WHERE status='PENDING_REVIEW' 조건에 안 걸리면(동시에 이미 처리됨) 0행 반영 -> 예외
        int updatedRows = applicationMapper.updateApplicationApproved(applicationId, finalPrice, paymentDueAt, reviewedAt);

        if (updatedRows == 0) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_PENDING_REVIEW);
        }

        // 알림
        Business business = businessMapper.selectById(application.getBusinessId());

        if(business != null) {
            notifyApplicationEventAfterCommit(business.getOwnerId(), NotificationType.VENDOR_APPLICATION_APPROVED,
                    "참가 신청이 승인되었습니다",
                    "참가비 " + finalPrice + "원을 " + paymentDueAt.toLocalDate() + "까지 결제해주세요.");
        }

        return ApplicationReviewResultResponse.builder()
                .applicationId(applicationId)
                .status(Application.Status.PAYMENT_PENDING.name())
                .finalPrice(finalPrice)
                .paymentDueAt(paymentDueAt)
                .reviewedAt(reviewedAt)
                .build();

    }

    // 참가 신청서 반려 (행사 담당자용)
    @Transactional
    public ApplicationReviewResultResponse rejectApplication(Long applicationId, ApplicationRejectRequest request) {

        // 신청 존재 확인
        Application application = applicationMapper.selectById(applicationId);

        if(application == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 이 신청이 속한 행사의 담당자가 요청자 본인인지 확인
        fairAdminAccessGuard.checkAssigned(application.getFairId());

        // 심사 대기 상태인지 확인
        if(application.getStatus() != Application.Status.PENDING_REVIEW) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_PENDING_REVIEW);
        }

        /*
         * 반려 사유 필수 확인 — DTO에 @NotBlank 대신 여기서 직접 검증
         * (구체적인 에러코드 APPLICATION_REJECT_REASON_REQUIRED를 반환하기 위해, TERMS_NOT_AGREED와 동일한 이유)
         */
        if(request.getRejectReason() == null || request.getRejectReason().isBlank()) {
            throw new CommonException(ErrorCode.APPLICATION_REJECT_REASON_REQUIRED);
        }

        // 반려 처리: PENDING_REVIEW 상태일 때만 전환 (동시 처리 방지)
        LocalDateTime reviewedAt = LocalDateTime.now();

        int updatedRows = applicationMapper.updateApplicationRejected(applicationId, request.getRejectReason(), reviewedAt);

        if(updatedRows == 0) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_PENDING_REVIEW);
        }

        // 슬롯 잠금 풀기
        unlockSlots(applicationId);

        // 알림
        Business business = businessMapper.selectById(application.getBusinessId());

        if(business != null) {
            notifyApplicationEventAfterCommit(business.getOwnerId(), NotificationType.VENDOR_APPLICATION_REJECTED,
                    "참가 신청이 반려되었습니다",
                    "반려 사유: " + request.getRejectReason());
        }

        return ApplicationReviewResultResponse.builder()
                .applicationId(applicationId)
                .status(Application.Status.REJECTED.name())
                .rejectReason(request.getRejectReason())
                .reviewedAt(reviewedAt)
                .build();

    }

    // 신청이 선택한 슬롯 전부를 fair 도메인에 잠금 요청한다(제출 시점부터 배치 편집기에서 못 건드리게)
    private void lockSlots(Long applicationId) {

        for (BoothSlotHallRef ref : applicationMapper.selectSlotHallRefsByApplicationId(applicationId)) {
            boothSlotService.lockBoothSlot(ref.getHallId(), ref.getBoothSlotId());
        }

    }

    // 신청이 더 이상 슬롯을 점유하지 않게 됐을 때(반려·취소) fair 도메인에 잠금 해제를 요청한다
    private void unlockSlots(Long applicationId) {

        for (BoothSlotHallRef ref : applicationMapper.selectSlotHallRefsByApplicationId(applicationId)) {
            boothSlotService.unlockBoothSlot(ref.getHallId(), ref.getBoothSlotId());
        }

    }

    // 참가 취소 요청 제출 (사업자용)
    @Transactional
    public void submitCancelRequest(Long ownerId, Long applicationId, ApplicationCancelRequestSubmitRequest request) {

        // 신청 존재 확인
        Application application = applicationMapper.selectById(applicationId);

        if(application == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 본인 소유 사업자의 신청인지 확인
        Business business = businessMapper.selectById(application.getBusinessId());

        if(business == null || !business.getOwnerId().equals(ownerId)) {
            throw new CommonException(ErrorCode.ACCESS_DENIED, "본인 소유의 신청만 취소 요청할 수 있습니다.");
        }

        // 취소 가능한 상태인지 확인 (승인 대기/반려/이미 취소된 신청서는 취소 요청 불가)
        if(application.getStatus() != Application.Status.PAYMENT_PENDING
                && application.getStatus() != Application.Status.CONFIRMED) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_CANCELABLE);
        }

        // 취소 요청 마감 기한 확인 (행사 시작 7일 전까지만 가능, 재요청도 동일 적용)
        LocalDate operationStartDate = applicationMapper.selectOperationStartDateByFairId(application.getFairId());

        if (operationStartDate != null && LocalDate.now().isAfter(operationStartDate.minusDays(7))) {
            throw new CommonException(ErrorCode.APPLICATION_CANCEL_DEADLINE_EXCEEDED);
        }

        // 이미 처리 대기 중인 취소 요청이 있는지 사전 확인
        if(applicationMapper.existsPendingCancelRequest(applicationId)) {
            throw new CommonException(ErrorCode.APPLICATION_CANCEL_REQUEST_DUPLICATE);
        }

        ApplicationCancelRequest cancelRequest = ApplicationCancelRequest.builder()
                .applicationId(applicationId)
                .reason(request.getReason())
                .build();

        /*
         * 취소 요청 저장 — 실제 중복 방지 방어선은 DB 유니크 제약(UK_APPLICATION_CANCEL_ACTIVE).
         * 사전 체크와 이 insert 사이에 동시 요청이 끼어들어도, DB가 물리적으로 막아주고
         * 여기서 DuplicateKeyException으로 잡아서 같은 에러코드로 응답한다.
         */
        try {
            applicationMapper.insertApplicationCancelRequest(cancelRequest);
        } catch(DuplicateKeyException e) {
            throw new CommonException(ErrorCode.APPLICATION_CANCEL_REQUEST_DUPLICATE, e);
        }

    }

    // 참가 취소 요청 승인 (행사 담당자용) — application.status도 CANCELED로 함께 전환
    @Transactional
    public ApplicationCancelRequestResultResponse approveCancelRequest(Long adminUserId, Long applicationId) {
        
        // 신청 존재 확인
        Application application = applicationMapper.selectById(applicationId);

        if(application == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 이 신청이 속한 행사의 담당자가 요청자 본인인지 확인
        fairAdminAccessGuard.checkAssigned(application.getFairId());

        // 처리 대기 중인 취소 요청 존재 확인
        ApplicationCancelRequest cancelRequest = applicationMapper.selectPendingCancelRequest(applicationId);

        if(cancelRequest == null) {
            throw new CommonException(ErrorCode.APPLICATION_CANCEL_REQUEST_NOT_FOUND);
        }

        LocalDateTime decidedAt = LocalDateTime.now();

        // 취소 요청 승인 처리 (동시 처리 방지)
        int cancelRequestUpdated = applicationMapper.updateCancelRequestApproved(cancelRequest.getCancelRequestId(), decidedAt);

        if(cancelRequestUpdated == 0) {
            throw new CommonException(ErrorCode.APPLICATION_CANCEL_REQUEST_NOT_FOUND);
        }

        // 신청 상태를 CANCELED로 전환 (동시 처리 방지)
        int applicationUpdated = applicationMapper.updateApplicationCanceled(applicationId);

        if(applicationUpdated == 0) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_CANCELABLE);
        }

        // 슬롯 잠금 풀기
        unlockSlots(applicationId);

        // 이전 상태가 CONFIRMED였다면(결제완료 상태) 즐겨찾기와 부스도 함께 삭제한다
        boolean wasConfirmed = application.getStatus() == Application.Status.CONFIRMED;

        if(wasConfirmed) {

            boothMapper.deleteFavoritesByApplicationId(applicationId);
            boothMapper.deleteBoothItemsByApplicationId(applicationId);
            boothMapper.deleteBoothByApplicationId(applicationId);

        }

        // 결제가 있었다면(CONFIRMED 상태였던 경우) 환불 처리. PAYMENT_PENDING 상태에서 취소된 경우 결제가 없어 null.
        Long paymentId = applicationMapper.selectPaymentIdByApplicationId(applicationId);

        if(paymentId != null) {
            refundService.refund(paymentId, adminUserId,
                    new RefundRequest(RefundReason.VENDOR_CANCEL, RequestedByDomain.VENDOR));
        }

        // 알림
        Business business = businessMapper.selectById(application.getBusinessId());

        if(business != null) {
            notifyApplicationEventAfterCommit(business.getOwnerId(), NotificationType.VENDOR_APPLICATION_CANCEL_APPROVED,
                    "참가 취소 요청이 승인되었습니다",
                    "신청이 취소 처리되었습니다.");
        }

        return ApplicationCancelRequestResultResponse.builder()
                .cancelRequestId(cancelRequest.getCancelRequestId())
                .applicationId(applicationId)
                .status(ApplicationCancelRequest.Status.APPROVED.name())
                .applicationStatus(Application.Status.CANCELED.name())
                .decidedAt(decidedAt)
                .boothDeleted(wasConfirmed)
                .build();
        
    }

    /*
     * 취소된 행사(fairs.canceled_at IS NOT NULL)에 속한 신청서 하나를 자동 취소 처리한다.
     * 환불은 여기서 호출하지 않는다 - fair 도메인의 FairCancelRefundJob이 취소된 행사의
     * COMPLETED 결제(VENDOR_FEE 포함)를 스스로 찾아 이미 환불 처리한다. 여기서
     * 또 refundService.refund()를 부르면 같은 결제를 두 도메인이 동시에 처리하려는
     * 꼴이라(RefundService의 UK_REFUND_PAYMENT 유니크 제약이 막아주긴 하지만) 책임이
     * 겹친다 - application.status 전환만 담당한다.
     */
    @Transactional
    public boolean cancelApplicationForCanceledFair(Long applicationId) {

        Application application = applicationMapper.selectById(applicationId);

        if(application == null) {
            return false;
        }

        boolean wasConfirmed = application.getStatus() == Application.Status.CONFIRMED;

        // 락 순서를 approveCancelRequest와 통일(취소요청 행 먼저)해서 교착상태 방지
        applicationMapper.lockPendingCancelRequestIfExists(applicationId);

        int updated = applicationMapper.updateApplicationCanceled(applicationId);

        if(updated == 0) {
            return false; // 0이면 이미 다른 경로로 처리됨(동시성) - 배치 카운트에서 제외
        }

        // 슬롯 잠금 풀기
        unlockSlots(applicationId);

        // 이전 상태가 CONFIRMED였다면(결제완료 상태) 즐겨찾기와 부스도 함께 삭제한다
        if(wasConfirmed) {

            boothMapper.deleteFavoritesByApplicationId(applicationId);
            boothMapper.deleteBoothItemsByApplicationId(applicationId);
            boothMapper.deleteBoothByApplicationId(applicationId);

        }

        // 딸려있던 처리 대기 중인 취소 요청이 있으면 함께 종료 처리 (없으면 0행, 정상)
        applicationMapper.closeRequestedCancelRequestByApplicationId(applicationId, LocalDateTime.now());

        return true;

    }

    // 참가 취소 요청 반려 (행사 담당자용) — application.status는 그대로 유지
    @Transactional
    public ApplicationCancelRequestResultResponse rejectCancelRequest(Long applicationId) {

        // 신청 존재 확인
        Application application = applicationMapper.selectById(applicationId);

        if(application == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 이 신청이 속한 행사의 담당자가 요청자 본인인지 확인
        fairAdminAccessGuard.checkAssigned(application.getFairId());

        // 처리 대기 중인 취소 요청 존재 확인
        ApplicationCancelRequest cancelRequest = applicationMapper.selectPendingCancelRequest(applicationId);

        if(cancelRequest == null) {
            throw new CommonException(ErrorCode.APPLICATION_CANCEL_REQUEST_NOT_FOUND);
        }

        LocalDateTime decidedAt = LocalDateTime.now();

        // 취소 요청 반려 처리 (동시 처리 방지)
        int updated = applicationMapper.updateCancelRequestRejected(cancelRequest.getCancelRequestId(), decidedAt);

        if(updated == 0) {
            throw new CommonException(ErrorCode.APPLICATION_CANCEL_REQUEST_NOT_FOUND);
        }

        // 알림
        Business business = businessMapper.selectById(application.getBusinessId());

        if(business != null) {
            notifyApplicationEventAfterCommit(business.getOwnerId(), NotificationType.VENDOR_APPLICATION_CANCEL_REJECTED,
                    "참가 취소 요청이 반려되었습니다",
                    "취소 요청이 반려되었습니다.");
        }

        return ApplicationCancelRequestResultResponse.builder()
                .cancelRequestId(cancelRequest.getCancelRequestId())
                .applicationId(applicationId)
                .status(ApplicationCancelRequest.Status.REJECTED.name())
                .applicationStatus(application.getStatus().name())
                .decidedAt(decidedAt)
                .boothDeleted(false)
                .build();

    }

    /*
     * 결제 도메인이 Toss 승인을 부르기 전에 먼저 이 메서드로 신청 상태를 확인한다.
     * 이미 취소/반려된 신청이면 카드 승인 자체를 시도하지 않고 여기서 막는다
     * (confirmVendorPayment와 동일한 상태 체크를 승인 전 시점에도 한 번 더 하는 것).
     */
    public void assertPayable(Long applicationId) {

        Application application = applicationMapper.selectById(applicationId);

        if (application == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        if (application.getStatus() != Application.Status.PAYMENT_PENDING) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_PAYMENT_PENDING);
        }

    }

    /*
     * 결제 도메인이 참가비(VENDOR_FEE) 결제 완료를 통지하면 신청 상태를 CONFIRMED로 전환한다.
     * 결제 도메인이 PaymentService.confirmPayment()에서 직접 이 메서드를 호출한다.
     */
    @Transactional
    public void confirmVendorPayment(Long applicationId, Long paymentId, Long paidAmount) {

        if (applicationId == null || paymentId == null || paidAmount == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Application application = applicationMapper.selectById(applicationId);

        if (application == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 이미 CONFIRMED면 재시도로 온 중복 통지일 가능성 — 같은 결제가 이미 반영된 거라면
        // 재처리하지 않고 조용히 성공 처리한다(멱등). 다른 결제라면 이상 상황이라 막는다.
        if (application.getStatus() == Application.Status.CONFIRMED) {

            Long existingPaymentId = applicationMapper.selectPaymentIdByApplicationId(applicationId);

            if (paymentId.equals(existingPaymentId)) {
                return;
            }

            throw new CommonException(ErrorCode.APPLICATION_PAYMENT_EVENT_CONFLICT);

        }

        if (application.getStatus() != Application.Status.PAYMENT_PENDING) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_PAYMENT_PENDING);
        }

        // 승인 시 확정된 finalPrice와 실제 결제 금액이 다르면 통지 위변조/오류로 보고 막는다.
        if (!paidAmount.equals(application.getFinalPrice())) {
            throw new CommonException(ErrorCode.APPLICATION_PAYMENT_AMOUNT_MISMATCH);
        }

        // 조건부 UPDATE로 동시 처리 방지 (WHERE status='PAYMENT_PENDING' 가드)
        int updated = applicationMapper.updateApplicationConfirmed(applicationId);

        if (updated == 0) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_PAYMENT_PENDING);
        }

        // 결제 완료로 확정됐으니 부스 프로필을 자동 생성한다.
        Booth booth = Booth.builder()
                .applicationId(applicationId)
                .businessId(application.getBusinessId())
                .confirmedAt(LocalDateTime.now())
                .build();

        boothMapper.insertBooth(booth);

    }

    /*
     * 참가 신청 관련 상태 변경을 사업자에게 알림으로 남긴다. 알림 저장이 실패해도
     * 본 로직(승인/반려/취소 처리)은 이미 끝난 뒤이므로 예외를 던져 되돌리지 않는다
     * (RefundService.notifyRefundCompleted와 동일한 이유).
     */
    private void notifyApplicationEvent(Long recipientUserId, NotificationType type, String title, String body) {

        try {

            notificationService.save(new SaveNotificationDto.Request(
                    recipientUserId,
                    RecipientType.VENDOR,
                    type,
                    title,
                    body,
                    null,
                    List.of(DeliveryChannel.IN_APP),
                    null
            ));

        } catch (Exception e) {
            log.error("참가 신청 알림 저장 실패. recipientUserId={}, type={}", recipientUserId, type, e);
        }

    }

    private void notifyApplicationEventAfterCommit(Long recipientUserId, NotificationType type, String title, String body) {

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyApplicationEvent(recipientUserId, type, title, body);
            }
        });

    }

    /*
     * 사업자가 취소 처리(REVOKED)됐을 때, 그 사업자로 넣은 진행 중인 신청서를 전부 취소하고
     * 결제가 있었으면 환불한다. cancelApplicationForCanceledFair와 달리 이 흐름을 대신
     * 처리해주는 배치 잡이 따로 없어서, 환불까지 여기서 직접 호출한다(approveCancelRequest와 동일한 방식).
     *
     * 사유는 VENDOR_CANCEL로 기록되지만, 실제로는 벤더 본인이 아니라 관리자가 사업자를
     * 취소 처리해서 강제로 취소되는 것이라 이름이 정확히 맞진 않는다.
     */
    @Transactional
    public void cancelApplicationsForRevokedBusiness(Long businessId, Long adminUserId) {

        List<Application> applications = applicationMapper.selectActiveApplicationsByBusinessId(businessId);

        // businessId가 이 메서드 전체에서 고정값이라 루프 밖에서 한 번만 조회
        Business business = businessMapper.selectById(businessId);

        // 환불은 커밋 후 처리하므로, 대상 결제ID만 루프에서 모아둔다
        List<Long> paymentIdsToRefund = new ArrayList<>();

        for (Application application : applications) {

            Long applicationId = application.getApplicationId();

            // 락 순서를 approveCancelRequest/cancelApplicationForCanceledFair와 통일해서 교착상태 방지
            applicationMapper.lockPendingCancelRequestIfExists(applicationId);

            int updatedRows = applicationMapper.updateApplicationCanceled(applicationId);

            if (updatedRows == 0) {
                continue; // 이미 다른 경로로 처리됨(동시성) - 건너뜀
            }

            unlockSlots(applicationId);

            /*
             * select 시점 스냅샷으로 CONFIRMED 여부를 판단하면 레이스가 생길 수 있어(코드래빗 리뷰
             * 반영), 상태로 분기하지 않고 매번 호출한다 - 부스가 없으면 0행 삭제로 조용히 끝난다.
             */
            boothMapper.deleteFavoritesByApplicationId(applicationId);
            boothMapper.deleteBoothItemsByApplicationId(applicationId);
            boothMapper.deleteBoothByApplicationId(applicationId);

            // 딸려있던 처리 대기 중인 취소 요청이 있으면 함께 종료 처리 (없으면 0행, 정상)
            applicationMapper.closeRequestedCancelRequestByApplicationId(applicationId, LocalDateTime.now());

            // 결제가 있었다면(CONFIRMED였던 경우) 환불 대상으로 모아둠 - 실제 호출은 커밋 후
            Long paymentId = applicationMapper.selectPaymentIdByApplicationId(applicationId);

            if (paymentId != null) {
                paymentIdsToRefund.add(paymentId);
            }

            // 알림 - 사업자 취소로 신청이 강제 취소됐다는 걸 소유자에게 알림
            if (business != null) {

                notifyApplicationEventAfterCommit(business.getOwnerId(),
                        NotificationType.VENDOR_APPLICATION_CANCEL_APPROVED,
                        "사업자 승인 취소로 참가 신청이 취소되었습니다",
                        "관리자가 사업자를 취소 처리하여 신청이 취소되었습니다.");

            }

        }

        /*
         * 환불(외부 결제 게이트웨이 호출)은 트랜잭션 커밋 후에 실행한다. 트랜잭션 안에서 하면
         * 중간에 한 건이라도 실패 시 이미 게이트웨이가 승인한 앞선 환불들은 롤백 안 되는데
         * DB(신청 취소, 부스 삭제, 사업자 REVOKED)만 롤백돼서 "환불은 됐는데 상태는 그대로"인
         * 불일치가 생긴다(코드래빗 리뷰 반영). RefundService의 UK_REFUND_PAYMENT 유니크 제약이
         * 중복 방지를 이미 해주니, 커밋 후 단계가 재시도돼도 안전하다.
         */
        if (!paymentIdsToRefund.isEmpty()) {

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {

                    for (Long paymentId : paymentIdsToRefund) {

                        refundService.refund(paymentId, adminUserId,
                                new RefundRequest(RefundReason.VENDOR_CANCEL, RequestedByDomain.VENDOR));

                    }

                }

            });

        }

    }

}
