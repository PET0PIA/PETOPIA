package com.ms.petopia.api.business.service;

import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.dto.response.BusinessResponse;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/*
 * BusinessService 단위 테스트.
 * 실제 DB(BusinessMapper)는 Mock으로 대체하고, Service의 로직(변환·예외처리)만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    // 실제 DB 접근 없이 가짜로 동작(리턴값을 직접 지정)
    @Mock
    private BusinessMapper businessMapper;

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

}
