package com.ms.petopia.api.business.service;

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/*
 * BusinessRegistrar 단위 테스트.
 * 실제 DB(BusinessMapper)는 Mock으로 대체하고, 중복 사업자등록번호 처리 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BusinessRegistrarTest {

    @Mock
    private BusinessMapper businessMapper;

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

    }

}
