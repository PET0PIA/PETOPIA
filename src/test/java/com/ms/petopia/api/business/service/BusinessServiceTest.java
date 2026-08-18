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
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;

/*
 * BusinessService 단위 테스트.
 * 실제 DB(BusinessMapper)는 Mock으로 대체하고, Service의 로직(변환·예외처리)만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    // 실제 DB 접근 없이 가짜로 동작(리턴값을 직접 지정)
    @Mock
    private BusinessMapper businessMapper;

    @Mock
    private NtsBusinessVerificationClient ntsClient;

    @Mock
    private BusinessRegistrar businessRegistrar;

    @Mock
    private StorageService storageService;

    @Mock
    private UserRoleService userRoleService;

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private BusinessService businessService;

    /*
     * 테스트용 Business 도메인 객체를 만드는 헬퍼 메서드.
     * businessId/ownerId/name/verifyStatus만 테스트마다 다르게 주고, 나머지 필드는 고정값 사용.
     * approvalStatus는 BusinessResponse.from()이 항상 참조하므로 기본값 APPROVED로 채워둔다
     * (이 값 자체가 중요한 테스트는 아래에서 직접 세팅한다).
     */
    private Business createBusiness(Long businessId, Long ownerId, String name, String verifyStatus) {

        Business business = new Business();

        business.setBusinessId(businessId);
        business.setOwnerId(ownerId);
        business.setName(name);
        business.setCeoName("홍길동");
        business.setBizRegNo("1234567890");
        business.setStartDate(LocalDate.of(2020, 3, 15));
        business.setAddress("서울시 마포구");
        business.setPhone("02-1234-5678");
        business.setWebsite("https://test.co.kr");
        business.setVerifyStatus(Business.VerifyStatus.valueOf(verifyStatus));
        business.setApprovalStatus(Business.ApprovalStatus.APPROVED);
        business.setCreatedAt(LocalDateTime.of(2026, 7, 29, 10, 0));

        return business;
    }

    private BusinessRegisterRequest createRequest() {

        BusinessRegisterRequest request = new BusinessRegisterRequest();

        request.setName("테스트업체");
        request.setCeoName("홍길동");
        request.setBizRegNo("1234567890");
        request.setStartDate(LocalDate.of(2020, 1, 1));
        request.setAddress("서울시 강남구 테스트로 1");
        request.setPhone("02-1234-5678");
        request.setWebsite("https://test.co.kr");
        request.setBusinessRegDocKey("tmp/document/reg.pdf");

        return request;

    }

    @Nested
    @DisplayName("사업자 등록")
    class RegisterBusiness {

        @Test
        @DisplayName("국세청 검증 통과하면 문서를 확정하고 심사 대기 상태로 저장한다")
        void savesAsPendingReviewWhenValid() {

            // given
            Long ownerId = 1L;
            BusinessRegisterRequest request = createRequest();

            Business savedBusiness = createBusiness(1L, ownerId, "테스트업체", "VERIFIED");
            savedBusiness.setApprovalStatus(Business.ApprovalStatus.PENDING_REVIEW);

            given(ntsClient.validate(request.getBizRegNo(), request.getCeoName(), request.getStartDate()))
                    .willReturn(true);
            given(storageService.confirm("tmp/document/reg.pdf", UploadPolicy.DOCUMENT))
                    .willReturn("uploads/document/reg.pdf");
            given(businessRegistrar.save(ownerId, request, Business.VerifyStatus.VERIFIED, "uploads/document/reg.pdf"))
                    .willReturn(savedBusiness);

            // when
            BusinessResponse result = businessService.registerBusiness(ownerId, request);

            // then
            assertThat(result.getVerifyStatus()).isEqualTo("VERIFIED");
            assertThat(result.getApprovalStatus()).isEqualTo("PENDING_REVIEW");
            verify(businessRegistrar).save(ownerId, request, Business.VerifyStatus.VERIFIED, "uploads/document/reg.pdf");

        }

        @Test
        @DisplayName("국세청 검증이 false면 문서 확정·저장 없이 예외를 던진다")
        void doesNotSaveWhenInvalid() {

            // given
            Long ownerId = 1L;
            BusinessRegisterRequest request = createRequest();

            given(ntsClient.validate(any(), any(), any())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> businessService.registerBusiness(ownerId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("사업자 정보를 확인할 수 없습니다");

            // 검증 실패로 막혔으니, 문서 확정·저장 자체가 절대 호출되면 안 됨
            verify(storageService, never()).confirm(any(), any());
            verify(businessRegistrar, never()).save(any(), any(), any(), any());

        }

        @Test
        @DisplayName("국세청 API 호출 자체가 실패하면 저장하지 않고 예외를 던진다")
        void doesNotSaveWhenApiThrows() {

            // given
            Long ownerId = 1L;
            BusinessRegisterRequest request = createRequest();

            given(ntsClient.validate(any(), any(), any()))
                    .willThrow(new IllegalStateException("국세청 API 호출 실패"));

            // when & then
            assertThatThrownBy(() -> businessService.registerBusiness(ownerId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("일시적으로 연결할 수 없습니다");

            // API 자체가 실패했으니, 저장도 절대 호출되면 안 됨
            verify(businessRegistrar, never()).save(any(), any(), any(), any());

        }

    }

    @Nested
    @DisplayName("내 사업자 목록 조회")
    class GetMyBusinesses {

        @Test
        @DisplayName("사업자가 여러 개면 전부 응답으로 변환해서 반환한다")
        void returnsAllBusinessesForOwner() {

            // given: Mapper가 사업자 2건을 리턴하도록 미리 설정
            Long ownerId = 1L;
            List<Business> businesses = List.of(
                    createBusiness(1L, ownerId, "멍냥사료", "VERIFIED"),
                    createBusiness(2L, ownerId, "클린포즈 미용실", "PENDING")
            );

            given(businessMapper.selectByOwnerId(ownerId)).willReturn(businesses);

            // when: Service의 목록 조회 메서드 실행
            List<BusinessResponse> result = businessService.getMyBusinesses(ownerId);

            // then: 2건이 그대로 응답 DTO로 변환됐는지, 필드값이 유지됐는지 확인
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("멍냥사료");
            assertThat(result.get(0).getVerifyStatus()).isEqualTo("VERIFIED");
            assertThat(result.get(1).getName()).isEqualTo("클린포즈 미용실");

            // Mapper가 정확히 이 ownerId로 한 번 호출됐는지도 검증
            verify(businessMapper).selectByOwnerId(ownerId);

        }

        @Test
        @DisplayName("등록된 사업자가 없으면 빈 리스트를 반환한다")
        void returnsEmptyListWhenNoBusiness() {

            // given: Mapper가 빈 리스트를 리턴하도록 설정(해당 owner가 사업자를 하나도 등록 안 한 경우)
            Long ownerId = 999L;

            given(businessMapper.selectByOwnerId(ownerId)).willReturn(List.of());

            // when
            List<BusinessResponse> result = businessService.getMyBusinesses(ownerId);

            // then: 예외 없이 빈 리스트가 그대로 반환되는지 확인
            assertThat(result).isEmpty();
            verify(businessMapper).selectByOwnerId(ownerId);

        }

    }

    @Nested
    @DisplayName("사업자 상세 조회")
    class GetBusiness {

        @Test
        @DisplayName("본인 소유 사업자면 응답으로 변환해서 반환한다")
        void returnsBusinessWhenExists() {

            // given: 조회하려는 사업자가 실제로 존재하고, 조회하는 사람(ownerId)이 그 사업자의 소유자와 일치하는 상황
            Long ownerId = 1L;
            Long businessId = 1L;
            Business business = createBusiness(businessId, ownerId, "멍냥사료", "VERIFIED");

            given(businessMapper.selectById(businessId)).willReturn(business);

            // when: 본인 소유 사업자를 상세 조회
            BusinessResponse result = businessService.getBusiness(ownerId, businessId);

            // then: 예외 없이 정상적으로 응답 DTO가 반환되고, 값이 정확한지 확인
            assertThat(result.getBusinessId()).isEqualTo(businessId);
            assertThat(result.getName()).isEqualTo("멍냥사료");

            verify(businessMapper).selectById(businessId);

        }

        @Test
        @DisplayName("본인 소유가 아니면 예외를 던진다")
        void throwsWhenNotOwner() {

            Long businessId = 1L;
            Business business = createBusiness(businessId, 1L, "멍냥사료", "VERIFIED");

            given(businessMapper.selectById(businessId)).willReturn(business);

            assertThatThrownBy(() -> businessService.getBusiness(2L, businessId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("본인 소유의 사업자만");

        }

        @Test
        @DisplayName("존재하지 않는 사업자면 예외를 던진다")
        void throwsWhenBusinessNotFound() {

            Long ownerId = 1L;
            Long businessId = 999L;

            given(businessMapper.selectById(businessId)).willReturn(null);

            assertThatThrownBy(() -> businessService.getBusiness(ownerId, businessId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("사업자를 찾을 수 없습니다");

        }

    }

    @Nested
    @DisplayName("심사 상태별 사업자 목록 조회")
    class GetBusinessesForReview {

        @Test
        @DisplayName("해당 상태의 사업자 목록을 요약 정보로 변환해서 반환한다")
        void returnsSummaryList() {

            // given: Mapper가 PENDING_REVIEW 상태 사업자 1건을 리턴하도록 설정
            Business business = createBusiness(1L, 1L, "멍냥사료", "VERIFIED");
            business.setApprovalStatus(Business.ApprovalStatus.PENDING_REVIEW);

            given(businessMapper.selectByApprovalStatus("PENDING_REVIEW")).willReturn(List.of(business));

            // when
            List<BusinessReviewSummaryResponse> result =
                    businessService.getBusinessesForReview(Business.ApprovalStatus.PENDING_REVIEW);

            // then: 요약 DTO로 변환되고, 요청한 상태값 그대로 Mapper에 넘어갔는지 확인
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getBusinessId()).isEqualTo(1L);
            assertThat(result.get(0).getVerifyStatus()).isEqualTo("VERIFIED");

        }

    }

    @Nested
    @DisplayName("사업자 심사 상세 조회")
    class GetBusinessReviewDetail {

        @Test
        @DisplayName("첨부서류가 있으면 공개 URL로 변환해서 내려준다")
        void returnsDetailWithDocumentUrl() {

            // given: 첨부서류 objectKey가 있는 사업자
            Business business = createBusiness(1L, 1L, "멍냥사료", "VERIFIED");
            business.setBusinessRegDocKey("uploads/document/reg.pdf");

            given(businessMapper.selectByIdForReview(1L)).willReturn(business);
            given(storageService.toPublicUrl("uploads/document/reg.pdf"))
                    .willReturn("https://cdn.petopia.kr/uploads/document/reg.pdf");

            // when
            BusinessReviewDetailResponse result = businessService.getBusinessReviewDetail(1L);

            // then: objectKey가 실제 공개 URL로 변환돼서 응답에 담기는지 확인
            assertThat(result.getDocumentUrl()).isEqualTo("https://cdn.petopia.kr/uploads/document/reg.pdf");

        }

        @Test
        @DisplayName("첨부서류가 없으면(구버전 자동승인) documentUrl은 null이다")
        void returnsNullDocumentUrlWhenNoDocKey() {

            // given: 구버전 자동승인 행이라 businessRegDocKey가 null인 상황
            Business business = createBusiness(1L, 1L, "멍냥사료", "VERIFIED");
            business.setBusinessRegDocKey(null);

            given(businessMapper.selectByIdForReview(1L)).willReturn(business);

            // when
            BusinessReviewDetailResponse result = businessService.getBusinessReviewDetail(1L);

            // then: objectKey 자체가 없으니 URL 변환 시도도 하면 안 됨
            assertThat(result.getDocumentUrl()).isNull();
            verify(storageService, never()).toPublicUrl(any());

        }

        @Test
        @DisplayName("존재하지 않는 사업자면 예외를 던진다")
        void throwsWhenNotFound() {

            // given: 존재하지 않는 businessId
            given(businessMapper.selectByIdForReview(999L)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> businessService.getBusinessReviewDetail(999L))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("사업자를 찾을 수 없습니다");

        }

    }

    @Nested
    @DisplayName("사업자 승인")
    class ApproveBusiness {

        @Test
        @DisplayName("첫 승인 사업자면 VENDOR 권한을 부여한다")
        void grantsVendorRoleWhenFirstApproved() {

            // given: 심사 대기 중인 사업자, 이 소유자에겐 아직 승인된 사업자가 없는 상황
            Business business = createBusiness(1L, 10L, "멍냥사료", "VERIFIED");

            given(businessMapper.selectByIdForReview(1L)).willReturn(business);
            given(businessMapper.updateApprovalApproved(eq(1L), eq(2L), any())).willReturn(1);
            given(businessMapper.existsApprovedBusinessForOwner(10L, 1L)).willReturn(false);

            // when
            BusinessReviewResultResponse result = businessService.approveBusiness(2L, 1L);

            // then: 승인 처리되고, 첫 승인이니 VENDOR 권한이 부여됐는지 확인
            assertThat(result.getApprovalStatus()).isEqualTo("APPROVED");
            verify(userRoleService).grantVendorRole(10L);

        }

        @Test
        @DisplayName("이미 승인된 사업자가 있으면 VENDOR 권한을 다시 부여하지 않는다")
        void doesNotGrantVendorRoleWhenAlreadyHasApprovedBusiness() {

            // given: 이 소유자가 이미 승인된 다른 사업자를 갖고 있는 상황
            Business business = createBusiness(1L, 10L, "멍냥사료", "VERIFIED");

            given(businessMapper.selectByIdForReview(1L)).willReturn(business);
            given(businessMapper.updateApprovalApproved(eq(1L), eq(2L), any())).willReturn(1);
            given(businessMapper.existsApprovedBusinessForOwner(10L, 1L)).willReturn(true);

            // when
            businessService.approveBusiness(2L, 1L);

            // then: 이미 VENDOR였을 테니 권한 부여 호출 자체가 없어야 함
            verify(userRoleService, never()).grantVendorRole(any());

        }

        @Test
        @DisplayName("심사 대기 상태가 아니면 예외를 던진다")
        void throwsWhenNotPendingReview() {

            // given: 이미 다른 관리자가 처리해서 조건부 UPDATE가 0행 반영된 상황(동시성)
            Business business = createBusiness(1L, 10L, "멍냥사료", "VERIFIED");

            given(businessMapper.selectByIdForReview(1L)).willReturn(business);
            given(businessMapper.updateApprovalApproved(eq(1L), eq(2L), any())).willReturn(0);

            // when & then
            assertThatThrownBy(() -> businessService.approveBusiness(2L, 1L))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("심사 대기 중인 사업자만");

            // 이미 처리된 거라, 뒤이은 role 관련 로직은 전혀 시도되면 안 됨
            verify(businessMapper, never()).existsApprovedBusinessForOwner(any(), any());
            verify(userRoleService, never()).grantVendorRole(any());

        }

        @Test
        @DisplayName("존재하지 않는 사업자면 예외를 던진다")
        void throwsWhenNotFound() {

            // given: 존재하지 않는 businessId
            given(businessMapper.selectByIdForReview(999L)).willReturn(null);

            // when & then
            assertThatThrownBy(() -> businessService.approveBusiness(2L, 999L))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("사업자를 찾을 수 없습니다");

        }

    }

    @Nested
    @DisplayName("사업자 반려")
    class RejectBusiness {

        @Test
        @DisplayName("정상적으로 반려 처리한다")
        void rejectsSuccessfully() {

            // given: 심사 대기 중인 사업자 + 반려 사유가 있는 요청
            Business business = createBusiness(1L, 10L, "멍냥사료", "VERIFIED");
            BusinessRejectRequest request = new BusinessRejectRequest();
            request.setRejectReason("서류 불일치");

            given(businessMapper.selectByIdForReview(1L)).willReturn(business);
            given(businessMapper.updateApprovalRejected(eq(1L), eq(2L), eq("서류 불일치"), any())).willReturn(1);

            // when
            BusinessReviewResultResponse result = businessService.rejectBusiness(2L, 1L, request);

            // then: 반려 상태와 사유가 그대로 응답에 담기는지 확인
            assertThat(result.getApprovalStatus()).isEqualTo("REJECTED");
            assertThat(result.getRejectReason()).isEqualTo("서류 불일치");

        }

        @Test
        @DisplayName("반려 사유가 없으면 예외를 던지고 UPDATE를 시도하지 않는다")
        void throwsWhenReasonBlank() {

            // given: 반려 사유가 공백인 요청(@NotBlank 대신 서비스가 직접 체크하는 경로)
            Business business = createBusiness(1L, 10L, "멍냥사료", "VERIFIED");
            BusinessRejectRequest request = new BusinessRejectRequest();
            request.setRejectReason("  ");

            given(businessMapper.selectByIdForReview(1L)).willReturn(business);

            // when & then
            assertThatThrownBy(() -> businessService.rejectBusiness(2L, 1L, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("반려 사유를 입력해야 합니다");

            // 사유 검증에서 막혔으니, UPDATE 자체가 시도되면 안 됨
            verify(businessMapper, never()).updateApprovalRejected(any(), any(), any(), any());

        }

        @Test
        @DisplayName("심사 대기 상태가 아니면 예외를 던진다")
        void throwsWhenNotPendingReview() {

            // given: 이미 처리된 사업자라 조건부 UPDATE가 0행 반영된 상황
            Business business = createBusiness(1L, 10L, "멍냥사료", "VERIFIED");
            BusinessRejectRequest request = new BusinessRejectRequest();
            request.setRejectReason("서류 불일치");

            given(businessMapper.selectByIdForReview(1L)).willReturn(business);
            given(businessMapper.updateApprovalRejected(eq(1L), eq(2L), eq("서류 불일치"), any())).willReturn(0);

            // when & then
            assertThatThrownBy(() -> businessService.rejectBusiness(2L, 1L, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("심사 대기 중인 사업자만");

        }

    }

    @Nested
    @DisplayName("승인된 사업자 취소 처리")
    class RevokeBusiness {

        @Test
        @DisplayName("정상 취소하면 신청서를 연쇄 취소하고, 남은 승인 사업자가 없으면 VENDOR 권한을 회수한다")
        void revokesAndDowngradesRoleWhenNoOtherApprovedBusiness() {

            // given: 승인 상태인 사업자, 취소 사유 있는 요청, 이 소유자에게 남은 승인 사업자가 없는 상황
            Business business = createBusiness(1L, 10L, "멍냥사료", "VERIFIED");
            BusinessRevokeRequest request = new BusinessRevokeRequest();
            request.setRevokeReason("조작 서류 발각");

            given(businessMapper.selectByIdForReview(1L)).willReturn(business);
            given(businessMapper.updateApprovalRevoked(eq(1L), eq(2L), eq("조작 서류 발각"), any())).willReturn(1);
            given(businessMapper.existsApprovedBusinessForOwner(10L, 1L)).willReturn(false);

            // when
            BusinessReviewResultResponse result = businessService.revokeBusiness(2L, 1L, request);

            // then: 취소 처리되고, 딸린 신청서 연쇄 취소 + VENDOR 권한 회수가 호출됐는지 확인
            assertThat(result.getApprovalStatus()).isEqualTo("REVOKED");
            verify(applicationService).cancelApplicationsForRevokedBusiness(1L, 2L);
            verify(userRoleService).revokeVendorRole(10L);

        }

        @Test
        @DisplayName("다른 승인된 사업자가 남아있으면 VENDOR 권한은 그대로 유지한다")
        void keepsVendorRoleWhenOtherApprovedBusinessRemains() {

            // given: 이 소유자에게 이 사업자 말고도 승인된 다른 사업자가 남아있는 상황
            Business business = createBusiness(1L, 10L, "멍냥사료", "VERIFIED");
            BusinessRevokeRequest request = new BusinessRevokeRequest();
            request.setRevokeReason("조작 서류 발각");

            given(businessMapper.selectByIdForReview(1L)).willReturn(business);
            given(businessMapper.updateApprovalRevoked(eq(1L), eq(2L), eq("조작 서류 발각"), any())).willReturn(1);
            given(businessMapper.existsApprovedBusinessForOwner(10L, 1L)).willReturn(true);

            // when
            businessService.revokeBusiness(2L, 1L, request);

            // then: 신청서 연쇄 취소는 그대로 일어나지만, 다른 사업자가 남아있으니 role은 안 건드림
            verify(applicationService).cancelApplicationsForRevokedBusiness(1L, 2L);
            verify(userRoleService, never()).revokeVendorRole(any());

        }

        @Test
        @DisplayName("취소 사유가 없으면 예외를 던지고 아무것도 처리하지 않는다")
        void throwsWhenReasonBlank() {

            // given: 취소 사유가 없는 요청
            Business business = createBusiness(1L, 10L, "멍냥사료", "VERIFIED");
            BusinessRevokeRequest request = new BusinessRevokeRequest();
            request.setRevokeReason(null);

            given(businessMapper.selectByIdForReview(1L)).willReturn(business);

            // when & then
            assertThatThrownBy(() -> businessService.revokeBusiness(2L, 1L, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("사유를 입력해야 합니다");

            // 사유 검증에서 막혔으니, UPDATE도 연쇄 취소도 전혀 시도되면 안 됨
            verify(businessMapper, never()).updateApprovalRevoked(any(), any(), any(), any());
            verify(applicationService, never()).cancelApplicationsForRevokedBusiness(any(), any());

        }

        @Test
        @DisplayName("승인 상태가 아니면 예외를 던지고 연쇄 취소를 시도하지 않는다")
        void throwsWhenNotApproved() {

            // given: 이미 REVOKED됐거나 애초에 승인 안 된 사업자라 조건부 UPDATE가 0행 반영된 상황
            Business business = createBusiness(1L, 10L, "멍냥사료", "VERIFIED");
            BusinessRevokeRequest request = new BusinessRevokeRequest();
            request.setRevokeReason("조작 서류 발각");

            given(businessMapper.selectByIdForReview(1L)).willReturn(business);
            given(businessMapper.updateApprovalRevoked(eq(1L), eq(2L), eq("조작 서류 발각"), any())).willReturn(0);

            // when & then
            assertThatThrownBy(() -> businessService.revokeBusiness(2L, 1L, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("승인된 사업자만");

            // UPDATE가 안 먹혔으니, 뒤이은 연쇄 취소는 시도되면 안 됨
            verify(applicationService, never()).cancelApplicationsForRevokedBusiness(any(), any());

        }

    }

}
