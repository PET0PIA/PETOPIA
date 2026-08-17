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
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessMapper businessMapper;
    private final NtsBusinessVerificationClient ntsClient;
    private final BusinessRegistrar businessRegistrar;
    private final StorageService storageService;
    private final UserRoleService userRoleService;
    private final ApplicationService applicationService;

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

        // 여기 도달하면 항상 VERIFIED. 저장은 별도 컴포넌트(트랜잭션 안)에서, 심사 대기 상태로 수행
        Business saved = businessRegistrar.save(ownerId, request, Business.VerifyStatus.VERIFIED, confirmedDocKey);

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
        if (!businessMapper.existsApprovedBusinessForOwner(business.getOwnerId())) {
            userRoleService.grantVendorRole(business.getOwnerId());
        }

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
        if (!businessMapper.existsApprovedBusinessForOwner(business.getOwnerId())) {
            userRoleService.revokeVendorRole(business.getOwnerId());
        }

        return BusinessReviewResultResponse.builder()
                .businessId(businessId)
                .approvalStatus(Business.ApprovalStatus.REVOKED.name())
                .rejectReason(request.getRevokeReason())
                .reviewedAt(reviewedAt)
                .build();

    }

}
