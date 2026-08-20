package com.ms.petopia.api.application.service;

import com.ms.petopia.api.application.domain.Application;
import com.ms.petopia.api.application.dto.response.ApplicationVendorFeePaymentContextResponse;
import com.ms.petopia.api.application.mapper.ApplicationMapper;
import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ApplicationPaymentContextServiceTest {

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private BusinessMapper businessMapper;
    @InjectMocks
    private ApplicationPaymentContextService service;

    @Test
    void exposesOnlyServerOwnedFinalPrice() {
        given(applicationMapper.selectById(10L)).willReturn(payableApplication());
        given(businessMapper.selectById(5L)).willReturn(ownedBusiness());

        ApplicationVendorFeePaymentContextResponse response = service.getPayableContext(10L);

        assertThat(response.applicationId()).isEqualTo(10L);
        assertThat(response.fairId()).isEqualTo(30L);
        assertThat(response.businessId()).isEqualTo(5L);
        assertThat(response.payerUserId()).isEqualTo(20L);
        assertThat(response.amount()).isEqualTo(50_000L);
        assertThat(response.paymentDueAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void throwsWhenApplicationNotFound() {
        given(applicationMapper.selectById(10L)).willReturn(null);

        assertThatThrownBy(() -> service.getPayableContext(10L))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    void throwsWhenNotPaymentPending() {
        Application application = payableApplication();
        application.setStatus(Application.Status.PENDING_REVIEW);
        given(applicationMapper.selectById(10L)).willReturn(application);

        assertThatThrownBy(() -> service.getPayableContext(10L))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_PAYMENT_PENDING);
    }

    @Test
    void throwsWhenFinalPriceMissing() {
        Application application = payableApplication();
        application.setFinalPrice(null);
        given(applicationMapper.selectById(10L)).willReturn(application);

        assertThatThrownBy(() -> service.getPayableContext(10L))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_PAYMENT_PENDING);
    }

    @Test
    void throwsWhenBusinessNotFound() {
        given(applicationMapper.selectById(10L)).willReturn(payableApplication());
        given(businessMapper.selectById(5L)).willReturn(null);

        assertThatThrownBy(() -> service.getPayableContext(10L))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_PAYMENT_PENDING);
    }

    @Test
    void throwsOnInvalidApplicationId() {
        assertThatThrownBy(() -> service.getPayableContext(0L))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void throwsWhenPaymentDueAtPassed() {
        Application application = payableApplication();
        application.setPaymentDueAt(LocalDateTime.now().minusMinutes(1));
        given(applicationMapper.selectById(10L)).willReturn(application);

        assertThatThrownBy(() -> service.getPayableContext(10L))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_NOT_PAYMENT_PENDING);
    }

    private Application payableApplication() {
        return Application.builder()
                .applicationId(10L)
                .businessId(5L)
                .fairId(30L)
                .status(Application.Status.PAYMENT_PENDING)
                .finalPrice(50_000L)
                .paymentDueAt(LocalDateTime.now().plusDays(1)) // 고정 날짜 대신 상대 시각
                .build();
    }

    private Business ownedBusiness() {
        return Business.builder()
                .businessId(5L)
                .ownerId(20L)
                .build();
    }
}