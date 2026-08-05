package com.ms.petopia.api.application.service;

import com.ms.petopia.api.application.domain.Application;
import com.ms.petopia.api.application.domain.ApplicationForm;
import com.ms.petopia.api.application.domain.ApplicationSlot;
import com.ms.petopia.api.application.dto.request.ApplicationSubmitRequest;
import com.ms.petopia.api.application.dto.response.ApplicationResponse;
import com.ms.petopia.api.application.dto.response.ApplicationSummaryResponse;
import com.ms.petopia.api.application.dto.response.BoothSlotLockStatusResponse;
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

        // 3) 행사 존재 확인
        if(!applicationMapper.existsFair(fairId)) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }

        // 3-1) 모집 마감 여부 확인 (RecruitNoticeService.isClosed()와 같은 기준)
        if(isRecruitClosed(fairId)) {
            throw new CommonException(ErrorCode.RECRUIT_CLOSED);
        }

        // 4) 중복 신청 확인
        if(applicationMapper.existsActiveApplication(request.getBusinessId(), fairId)) {
            throw new CommonException(ErrorCode.APPLICATION_DUPLICATE_ACTIVE);
        }

        // 5) 슬롯 중복 선택 확인 + 존재/잠금 확인 (GET에서 쓴 쿼리 재사용)
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
         * 최종 잠금 확인 (FOR UPDATE) — 이 시점 이후 커밋될 때까지 이 슬롯들은 다른 트랜잭션이 못 건드림
         * 슬롯 ID를 정렬해서 잠가야 여러 슬롯 잠금 시 데드락이 안 생김
         */
        List<Long> sortedSlotIds = request.getBoothSlotIds().stream().sorted().toList();
        List<Long> lockedNow = applicationMapper.selectLockedBoothSlotIdsForUpdate(sortedSlotIds);

        if (!lockedNow.isEmpty()) {
            throw new CommonException(ErrorCode.BOOTH_SLOT_ALREADY_LOCKED);
        }

        return slotsById;

    }

    // 모집 공고 마감 판정 (RecruitNoticeService.isClosed()와 동일 기준: 마감일 지남/행사취소/행사종료)
    private boolean isRecruitClosed(Long fairId) {

        RecruitNotice notice = recruitNoticeMapper.selectByFairId(fairId);
        FairStatusInfo fairStatus = recruitNoticeMapper.selectFairStatusByFairId(fairId);

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

}
