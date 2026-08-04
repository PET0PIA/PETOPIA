package com.ms.petopia.api.business.service;

import com.ms.petopia.api.auth.service.UserRoleService;
import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.dto.request.BusinessRegisterRequest;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.global.exception.CommonException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;

/*
 * BusinessRegistrar 단위 테스트.
 * 실제 DB(BusinessMapper)는 Mock으로 대체하고, 중복 사업자등록번호 처리 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BusinessRegistrarTest {

    @Mock
    private BusinessMapper businessMapper;

    @Mock
    private UserRoleService userRoleService;

    @InjectMocks
    private BusinessRegistrar businessRegistrar;

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

    private Business createBusiness(Long businessId, Long ownerId, String bizRegNo, Business.VerifyStatus verifyStatus) {

        Business business = new Business();

        business.setBusinessId(businessId);
        business.setOwnerId(ownerId);
        business.setName("테스트업체");
        business.setCeoName("홍길동");
        business.setBizRegNo(bizRegNo);
        business.setStartDate(LocalDate.of(2020, 1, 1));
        business.setAddress("서울시 강남구 테스트로 1");
        business.setPhone("02-1234-5678");
        business.setWebsite("https://test.co.kr");
        business.setVerifyStatus(verifyStatus);

        return business;

    }

    @Test
    @DisplayName("이미 등록된 사업자등록번호면 BUSINESS_DUPLICATE 예외를 던진다")
    void throwsWhenBizRegNoDuplicated() {

        // given: insert 시점에 DB unique 제약(biz_reg_no) 위반이 발생하는 상황을 재현
        Long ownerId = 1L;
        BusinessRegisterRequest request = createRequest();

        doThrow(new DuplicateKeyException("UK_BUSINESS_BIZ_REG_NO"))
                .when(businessMapper).insertBusiness(any(Business.class));

        // when & then
        assertThatThrownBy(() ->
                businessRegistrar.save(ownerId, request, Business.VerifyStatus.VERIFIED))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("이미 등록된 사업자등록번호");

        // 저장이 실패했으니, 재조회(selectById)까지 가면 안 됨
        verify(businessMapper, never()).selectById(any());
        verify(userRoleService, never()).grantVendorRole(any());

    }

    @Test
    @DisplayName("정상 저장되면 role도 VENDOR로 전환한다")
    void savesBusinessAndGrantsVendorRole() {

        // given
        Long ownerId = 1L;
        BusinessRegisterRequest request = createRequest();
        Business saved = createBusiness(1L, ownerId, "1234567890", Business.VerifyStatus.VERIFIED);

        // 첫 사업자
        given(businessMapper.selectByOwnerId(ownerId)).willReturn(List.of());
        given(businessMapper.selectById(any())).willReturn(saved);

        // when
        Business result = businessRegistrar.save(ownerId, request, Business.VerifyStatus.VERIFIED);

        // then: insert → role 전환 → 재조회, 세 가지가 다 일어났는지 확인
        assertThat(result.getBusinessId()).isEqualTo(1L);
        assertThat(result.getVerifyStatus()).isEqualTo(Business.VerifyStatus.VERIFIED);

        verify(businessMapper).insertBusiness(any(Business.class));
        verify(userRoleService).grantVendorRole(ownerId);

    }

    @Test
    @DisplayName("이미 사업자가 있으면(두 번째 등록부터) role을 다시 전환하지 않는다")
    void doesNotGrantVendorRoleWhenNotFirstBusiness() {

        // given: 이 owner가 이미 사업자 1건을 갖고 있는 상황
        Long ownerId = 1L;
        BusinessRegisterRequest request = createRequest();
        Business existing = createBusiness(1L, ownerId, "1234567890", Business.VerifyStatus.VERIFIED);
        Business saved = createBusiness(2L, ownerId, "9876543210", Business.VerifyStatus.VERIFIED);

        given(businessMapper.selectByOwnerId(ownerId)).willReturn(List.of(existing));
        given(businessMapper.selectById(any())).willReturn(saved);

        // when
        businessRegistrar.save(ownerId, request, Business.VerifyStatus.VERIFIED);

        // then: 이미 VENDOR였을 테니 role 전환 호출 자체가 없어야 함
        verify(businessMapper).insertBusiness(any(Business.class));
        verify(userRoleService, never()).grantVendorRole(any());

    }

}
