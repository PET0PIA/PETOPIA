package com.ms.petopia.api.application.service;

import com.ms.petopia.api.application.domain.Application;
import com.ms.petopia.api.application.domain.ApplicationCancelRequest;
import com.ms.petopia.api.application.domain.ApplicationForm;
import com.ms.petopia.api.application.domain.ApplicationSlot;
import com.ms.petopia.api.application.dto.request.ApplicationApproveRequest;
import com.ms.petopia.api.application.dto.request.ApplicationCancelRequestSubmitRequest;
import com.ms.petopia.api.application.dto.request.ApplicationRejectRequest;
import com.ms.petopia.api.application.dto.request.ApplicationSubmitRequest;
import com.ms.petopia.api.application.dto.response.*;
import com.ms.petopia.api.application.mapper.ApplicationMapper;
import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.api.recruitnotice.domain.FairStatusInfo;
import com.ms.petopia.api.recruitnotice.domain.RecruitNotice;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationMapper applicationMapper;
    private final BusinessMapper businessMapper;
    private final RecruitNoticeMapper recruitNoticeMapper;

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
                .attachmentUrl(request.getAttachmentUrl())
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

    // 내 신청 현황 목록 조회 (businessId는 선택적 필터)
    public List<ApplicationSummaryResponse> getMyApplications(Long ownerId, Long businessId) {

        return applicationMapper.selectMyApplications(ownerId, businessId);

    }

    // 신청 상세 조회
    public ApplicationDetailResponse getApplicationDetail(Long ownerId, Long applicationId) {

        ApplicationDetailResponse detail = applicationMapper.selectApplicationDetail(applicationId);

        if(detail == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 본인 소유 사업자의 신청인지 확인
        Business business = businessMapper.selectById(detail.getBusinessId());

        if(business == null || !business.getOwnerId().equals(ownerId)) {
            throw new CommonException(ErrorCode.ACCESS_DENIED, "본인 소유의 신청만 조회할 수 있습니다.");
        }

        // 선택 슬롯 목록 채우기
        detail.setSlots(applicationMapper.selectApplicationSlotDetails(applicationId));

        // 취소 요청 가능 여부 계산 (프론트 버튼 활성화 판단용)
        detail.setCancelable(isCancelable(detail));

        return detail;

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
    public List<ApplicationReviewSummaryResponse> getApplicationsForFair(Long adminUserId, Long fairId, String status) {

        // 이 행사의 담당자가 요청자 본인인지 확인
        verifyFairAdmin(adminUserId, fairId);

        return applicationMapper.selectApplicationsByFair(fairId, status);

    }

    // 담당 행사의 취소 요청 목록 조회 (행사 담당자용)
    public List<ApplicationCancelRequestSummaryResponse> getCancelRequestsForFair(Long adminUserId, Long fairId, String status) {

        // 이 행사의 담당자가 요청자 본인인지 확인
        verifyFairAdmin(adminUserId, fairId);

        return applicationMapper.selectCancelRequestsByFair(fairId, status);

    }

    // 참가 신청서 승인 (행사 담당자용)
    @Transactional
    public ApplicationReviewResultResponse approveApplication(Long adminUserId, Long applicationId, ApplicationApproveRequest request) {

        // 신청 존재 확인
        Application application = applicationMapper.selectById(applicationId);

        if(application == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 이 신청이 속한 행사의 담당자가 요청자 본인인지 확인
        verifyFairAdmin(adminUserId, application.getFairId());

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
    public ApplicationReviewResultResponse rejectApplication(Long adminUserId, Long applicationId, ApplicationRejectRequest request) {

        // 신청 존재 확인
        Application application = applicationMapper.selectById(applicationId);

        if(application == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 이 신청이 속한 행사의 담당자가 요청자 본인인지 확인
        verifyFairAdmin(adminUserId, application.getFairId());

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

        return ApplicationReviewResultResponse.builder()
                .applicationId(applicationId)
                .status(Application.Status.REJECTED.name())
                .rejectReason(request.getRejectReason())
                .reviewedAt(reviewedAt)
                .build();

    }

    // 담당자 권한 확인 공용 헬퍼
    private void verifyFairAdmin(Long adminUserId, Long fairId) {

        Long fairAdminUserId = recruitNoticeMapper.selectAdminUserIdByFairId(fairId);

        // 담당자가 아예 배정 안 된 행사인 경우
        if(fairAdminUserId == null) {
            throw new CommonException(ErrorCode.APPLICATION_ACCESS_DENIED, "담당자가 배정되지 않은 행사입니다.");
        }

        // 담당자는 있지만 요청자 본인이 아닌 경우
        if(!fairAdminUserId.equals(adminUserId)) {
            throw new CommonException(ErrorCode.APPLICATION_ACCESS_DENIED, "본인이 담당하는 행사가 아닙니다.");
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

}
