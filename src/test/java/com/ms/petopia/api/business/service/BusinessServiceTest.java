package com.ms.petopia.api.business.service;

import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.dto.request.BusinessRegisterRequest;
import com.ms.petopia.api.business.dto.response.BusinessResponse;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.global.exception.CommonException;
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

    // 위 businessMapper가 자동으로 주입된 테스트 대상
    @InjectMocks
    private BusinessService businessService;

    /*
     * 테스트용 Business 도메인 객체를 만드는 헬퍼 메서드.
     * businessId/ownerId/name/verifyStatus만 테스트마다 다르게 주고, 나머지 필드는 고정값 사용.
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

        return request;

    }

    @Nested
    @DisplayName("사업자 등록")
    class RegisterBusiness {

        @Test
        @DisplayName("국세청 검증 통과하면 VERIFIED로 저장한다")
        void savesAsVerifiedWhenValid() {

            // given
            Long ownerId = 1L;
            BusinessRegisterRequest request = createRequest();

            Business savedBusiness = createBusiness(1L, ownerId, "테스트업체", "VERIFIED");

            given(ntsClient.validate(request.getBizRegNo(), request.getCeoName(), request.getStartDate()))
                    .willReturn(true);
            given(businessRegistrar.save(ownerId, request, Business.VerifyStatus.VERIFIED))
                    .willReturn(savedBusiness);

            // when
            BusinessResponse result = businessService.registerBusiness(ownerId, request);

            // then
            assertThat(result.getVerifyStatus()).isEqualTo("VERIFIED");
            verify(businessRegistrar).save(ownerId, request, Business.VerifyStatus.VERIFIED);

        }

        @Test
        @DisplayName("국세청 검증이 false면 저장하지 않고 예외를 던진다")
        void doesNotSaveWhenInvalid() {

            // given
            Long ownerId = 1L;
            BusinessRegisterRequest request = createRequest();

            given(ntsClient.validate(any(), any(), any())).willReturn(false);

            // when & then
            assertThatThrownBy(() -> businessService.registerBusiness(ownerId, request))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("사업자 정보를 확인할 수 없습니다");

            // 검증 실패로 막혔으니, 저장 자체가 절대 호출되면 안 됨
            verify(businessRegistrar, never()).save(any(), any(), any());

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
            verify(businessRegistrar, never()).save(any(), any(), any());

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

            /*
             * given: business의 실제 owner는 1L인데, 조회 시도하는 사람은 2L
             * (Mapper 입장에서는 사업자가 정상적으로 조회되지만, 소유자가 다른 상황을 재현)
             */
            Long businessId = 1L;
            Business business = createBusiness(businessId, 1L, "멍냥사료", "VERIFIED");

            given(businessMapper.selectById(businessId)).willReturn(business);

            /*
             * when & then: 소유자가 아닌 2L로 조회를 시도하면 CommonException(권한 없음)이 발생해야 함
             * (예외가 던져지는 것 자체가 검증 대상이라 given/when/then을 분리하지 않고 한 블록으로 처리)
             */
            assertThatThrownBy(() -> businessService.getBusiness(2L, businessId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("본인 소유의 사업자만");

        }

        @Test
        @DisplayName("존재하지 않는 사업자면 예외를 던진다")
        void throwsWhenBusinessNotFound() {

            // given: 조회하려는 businessId에 해당하는 사업자가 DB에 아예 없는 상황(Mapper가 null 리턴)
            Long ownerId = 1L;
            Long businessId = 999L;

            given(businessMapper.selectById(businessId)).willReturn(null);

            // when & then: 존재하지 않는 사업자를 조회하면 CommonException(사업자 없음)이 발생해야 함
            assertThatThrownBy(() -> businessService.getBusiness(ownerId, businessId))
                    .isInstanceOf(CommonException.class)
                    .hasMessageContaining("사업자를 찾을 수 없습니다");

        }

    }

}
