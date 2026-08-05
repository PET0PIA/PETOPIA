package com.ms.petopia.api.application.service;

import com.ms.petopia.api.application.domain.Application;
import com.ms.petopia.api.application.domain.ApplicationForm;
import com.ms.petopia.api.application.domain.ApplicationSlot;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

        return detail;

    }

    // 담당 행사의 신청 목록 조회 (행사 담당자용)
    public List<ApplicationReviewSummaryResponse> getApplicationsForFair(Long adminUserId, Long fairId, String status) {

        Long fairAdminUserId = recruitNoticeMapper.selectAdminUserIdByFairId(fairId);

        if(fairAdminUserId == null) {
            throw new CommonException(ErrorCode.APPLICATION_ACCESS_DENIED, "담당자가 배정되지 않은 행사입니다.");
        }

        if(!fairAdminUserId.equals(adminUserId)) {
            throw new CommonException(ErrorCode.APPLICATION_ACCESS_DENIED, "본인이 담당하는 행사가 아닙니다.");
        }

        return applicationMapper.selectApplicationsByFair(fairId, status);

    }

}
