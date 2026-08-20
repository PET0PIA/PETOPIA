package com.ms.petopia.api.business.service;

import com.ms.petopia.api.application.service.ApplicationService;
import com.ms.petopia.api.auth.service.UserRoleService;
import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.dto.request.BusinessRegisterRequest;
import com.ms.petopia.api.business.dto.request.BusinessRejectRequest;
import com.ms.petopia.api.business.dto.request.BusinessRevokeRequest;
import com.ms.petopia.api.business.dto.response.BusinessResponse;
import com.ms.petopia.api.business.dto.response.BusinessReviewDetailResponse;
import com.ms.petopia.api.business.dto.response.BusinessReviewResultResponse;
import com.ms.petopia.api.business.dto.response.BusinessReviewSummaryResponse;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessMapper businessMapper;
    private final NtsBusinessVerificationClient ntsClient;
    private final BusinessRegistrar businessRegistrar;
    private final StorageService storageService;
    private final UserRoleService userRoleService;
    private final ApplicationService applicationService;
    private final NotificationService notificationService;

    // 사업자 등록(국세청 진위확인 포함)
    public BusinessResponse registerBusiness(Long ownerId, BusinessRegisterRequest request) {

        // 국세청 진위 확인 API 호출(동기) - 트랜잭션 없이, DB 커넥션 안 붙잡은 상태로 호출
        boolean valid;

        try {

            valid = ntsClient.validate(
                    request.getBizRegNo(),
                    request.getCeoName(),
                    request.getStartDate()
            );

        }catch (Exception e) {
            /*
             * API 호출 자체가 실패한 경우 - 아무것도 저장하지 않고 바로 에러 응답
             * (이러면 사용자가 같은 정보로 바로 재시도 가능, UK 충돌도 안 생김)
             */
            throw new CommonException(ErrorCode.NTS_API_UNAVAILABLE);
        }

        // 진위확인 실패(INVALID) - 등록 자체를 막음
        if (!valid) {
            throw new CommonException(ErrorCode.BUSINESS_VERIFICATION_FAILED);
        }

        // 첨부서류 확정(tmp -> uploads). S3 I/O라 NTS 호출과 같은 이유로 트랜잭션 밖에서 수행
        String confirmedDocKey = storageService.confirm(request.getBusinessRegDocKey(), UploadPolicy.DOCUMENT);

        Business saved;

        try {

            // 여기 도달하면 항상 VERIFIED. 저장은 별도 컴포넌트(트랜잭션 안)에서, 심사 대기 상태로 수행
            saved = businessRegistrar.save(ownerId, request, Business.VerifyStatus.VERIFIED, confirmedDocKey);

        } catch (CommonException e) {

            /*
             * 저장 실패(중복 사업자등록번호, 동시 등록 락 타임아웃 등) 시 이미 확정해둔 문서가
             * 사업자 레코드 없이 스토리지에 고아로 남는 것을 방지한다(코드래빗 리뷰 반영).
             * 삭제 자체가 실패해도 원래 예외를 가려버리면 안 되니 로그만 남기고 그대로 재던진다.
             */
            try {
                storageService.delete(confirmedDocKey);
            } catch (Exception deleteException) {
                log.error("사업자 등록 실패 후 고아 문서 삭제 실패: {}", confirmedDocKey, deleteException);
            }

            throw e;

        }

        return BusinessResponse.from(saved);

    }

    // 내 사업자 목록 조회
    public List<BusinessResponse> getMyBusinesses(Long ownerId) {

        List<Business> businesses = businessMapper.selectByOwnerId(ownerId);

        return businesses.stream()
                .map(BusinessResponse::from)
                .toList();

    }

    // 사업자 상세 조회(진위확인·심사 상태 포함)
    public BusinessResponse getBusiness(Long ownerId, Long businessId) {

        Business business = businessMapper.selectById(businessId);

        if(business == null) {
            throw new CommonException(ErrorCode.BUSINESS_NOT_FOUND);
        }

        if(!business.getOwnerId().equals(ownerId)) {
            throw new CommonException(ErrorCode.ACCESS_DENIED, "본인 소유의 사업자만 조회할 수 있습니다.");
        }

        return BusinessResponse.from(business);

    }

    // 심사 상태별 사업자 목록 조회 (관리자용, 기본 심사 대기)
    public List<BusinessReviewSummaryResponse> getBusinessesForReview(Business.ApprovalStatus approvalStatus) {

        return businessMapper.selectByApprovalStatus(approvalStatus.name()).stream()
                .map(b -> new BusinessReviewSummaryResponse(
                        b.getBusinessId(), b.getName(), b.getCeoName(), b.getBizRegNo(),
                        b.getVerifyStatus().name(), b.getCreatedAt()))
                .toList();

    }

    // 사업자 심사 상세 조회 (관리자용, 소유자 체크 없음)
    public BusinessReviewDetailResponse getBusinessReviewDetail(Long businessId) {

        Business business = businessMapper.selectByIdForReview(businessId);

        if (business == null) {
            throw new CommonException(ErrorCode.BUSINESS_NOT_FOUND);
        }

        String documentUrl = business.getBusinessRegDocKey() != null
                ? storageService.toPublicUrl(business.getBusinessRegDocKey())
                : null;

        return BusinessReviewDetailResponse.from(business, documentUrl);

    }

    // 사업자 승인 (관리자용)
    @Transactional
    public BusinessReviewResultResponse approveBusiness(Long reviewerId, Long businessId) {

        Business business = businessMapper.selectByIdForReview(businessId);

        if (business == null) {
            throw new CommonException(ErrorCode.BUSINESS_NOT_FOUND);
        }

        LocalDateTime reviewedAt = LocalDateTime.now();

        // WHERE approval_status='PENDING_REVIEW' 조건에 안 걸리면(동시에 이미 처리됨) 0행 반영 -> 예외
        int updatedRows = businessMapper.updateApprovalApproved(businessId, reviewerId, reviewedAt);

        if (updatedRows == 0) {
            throw new CommonException(ErrorCode.BUSINESS_NOT_PENDING_REVIEW);
        }

        // 이 소유자의 첫 승인 사업자일 때만 VENDOR 권한 부여 (두 번째부턴 이미 VENDOR)
        if (!businessMapper.existsApprovedBusinessForOwner(business.getOwnerId(), businessId)) {
            userRoleService.grantVendorRole(business.getOwnerId());
        }

        // 알림
        notifyBusinessEventAfterCommit(business.getOwnerId(), NotificationType.BUSINESS_APPROVED,
                "사업자 등록이 승인되었습니다",
                "사업자 등록이 승인되어 참가 신청이 가능합니다.");

        return BusinessReviewResultResponse.builder()
                .businessId(businessId)
                .approvalStatus(Business.ApprovalStatus.APPROVED.name())
                .reviewedAt(reviewedAt)
                .build();

    }

    // 사업자 반려 (관리자용)
    @Transactional
    public BusinessReviewResultResponse rejectBusiness(Long reviewerId, Long businessId,
                                                       BusinessRejectRequest request) {

        Business business = businessMapper.selectByIdForReview(businessId);

        if (business == null) {
            throw new CommonException(ErrorCode.BUSINESS_NOT_FOUND);
        }

        // 반려 사유 필수 확인 - DTO에 @NotBlank 대신 여기서 직접 검증(구체적인 에러코드 반환 위해)
        if (request.getRejectReason() == null || request.getRejectReason().isBlank()) {
            throw new CommonException(ErrorCode.BUSINESS_REJECT_REASON_REQUIRED);
        }

        LocalDateTime reviewedAt = LocalDateTime.now();

        int updatedRows = businessMapper.updateApprovalRejected(businessId, reviewerId, request.getRejectReason(), reviewedAt);

        if (updatedRows == 0) {
            throw new CommonException(ErrorCode.BUSINESS_NOT_PENDING_REVIEW);
        }

        // 알림
        notifyBusinessEventAfterCommit(business.getOwnerId(), NotificationType.BUSINESS_REJECTED,
                "사업자 등록이 반려되었습니다",
                "반려 사유: " + request.getRejectReason());

        return BusinessReviewResultResponse.builder()
                .businessId(businessId)
                .approvalStatus(Business.ApprovalStatus.REJECTED.name())
                .rejectReason(request.getRejectReason())
                .reviewedAt(reviewedAt)
                .build();

    }

    // 승인된 사업자 취소 처리 (관리자용, 예: 나중에 조작 서류로 밝혀진 경우)
    @Transactional
    public BusinessReviewResultResponse revokeBusiness(Long reviewerId, Long businessId, BusinessRevokeRequest request) {

        Business business = businessMapper.selectByIdForReview(businessId);

        if (business == null) {
            throw new CommonException(ErrorCode.BUSINESS_NOT_FOUND);
        }

        if (request.getRevokeReason() == null || request.getRevokeReason().isBlank()) {
            throw new CommonException(ErrorCode.BUSINESS_REVOKE_REASON_REQUIRED);
        }

        LocalDateTime reviewedAt = LocalDateTime.now();

        // WHERE approval_status='APPROVED' 조건에 안 걸리면(이미 취소됐거나 애초에 승인 안 됨) 0행 -> 예외
        int updatedRows = businessMapper.updateApprovalRevoked(businessId, reviewerId, request.getRevokeReason(), reviewedAt);

        if (updatedRows == 0) {
            throw new CommonException(ErrorCode.BUSINESS_NOT_APPROVED);
        }

        // 이 사업자로 진행 중이던 신청서 전부 취소(+환불)
        applicationService.cancelApplicationsForRevokedBusiness(businessId, reviewerId);

        // 이 소유자에게 남은 승인된 사업자가 하나도 없으면 VENDOR 권한 회수(다시 USER로)
        if (!businessMapper.existsApprovedBusinessForOwner(business.getOwnerId(), businessId)) {
            userRoleService.revokeVendorRole(business.getOwnerId());
        }

        // 알림
        notifyBusinessEventAfterCommit(business.getOwnerId(), NotificationType.BUSINESS_REVOKED,
                "사업자 등록이 취소되었습니다",
                "취소 사유: " + request.getRevokeReason());

        return BusinessReviewResultResponse.builder()
                .businessId(businessId)
                .approvalStatus(Business.ApprovalStatus.REVOKED.name())
                .rejectReason(request.getRevokeReason())
                .reviewedAt(reviewedAt)
                .build();

    }

    /*
     * 사업자 심사 결과를 소유자에게 알림으로 남긴다. 알림 저장이 실패해도
     * 본 로직(승인/반려/취소 처리)은 이미 끝난 뒤이므로 예외를 던져 되돌리지 않는다
     * (ApplicationService.notifyApplicationEvent와 동일한 이유).
     */
    private void notifyBusinessEvent(Long recipientUserId, NotificationType type, String title, String body) {

        try {

            notificationService.save(new SaveNotificationDto.Request(
                    recipientUserId,
                    RecipientType.VENDOR,
                    type,
                    title,
                    body,
                    null,
                    List.of(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
                    null
            ));

        } catch (Exception e) {
            log.error("사업자 심사 알림 저장 실패. recipientUserId={}, type={}", recipientUserId, type, e);
        }

    }

    private void notifyBusinessEventAfterCommit(Long recipientUserId, NotificationType type, String title, String body) {

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifyBusinessEvent(recipientUserId, type, title, body);
            }
        });

    }

}
