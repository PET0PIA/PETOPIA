package com.ms.petopia.api.payment.controller;

import com.ms.petopia.api.payment.dto.TossWebhookEvent;
import com.ms.petopia.api.payment.service.PaymentService;
import com.ms.petopia.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * PaymentWebhookController 통합 테스트. 인증 없이(permitAll) 토스가 직접 호출하는
 * 엔드포인트라 @AuthenticationPrincipal 관련 설정이 필요 없다 — 위조 방지는
 * PaymentService.handleDepositWebhook 내부의 secret 대조가 담당(PaymentServiceTest 참고).
 */
@ExtendWith(MockitoExtension.class)
class PaymentWebhookControllerTest {

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentWebhookController(paymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void handlesDepositCallback() throws Exception {
        mockMvc.perform(post("/webhooks/toss/deposit-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"DEPOSIT_CALLBACK\",\"data\":{\"orderId\":\"PAYMENT_1\","
                                + "\"status\":\"DONE\",\"secret\":\"secret-abc\"}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<TossWebhookEvent> captor = ArgumentCaptor.forClass(TossWebhookEvent.class);
        verify(paymentService).handleDepositWebhook(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("DEPOSIT_CALLBACK");
        assertThat(captor.getValue().data().orderId()).isEqualTo("PAYMENT_1");
        assertThat(captor.getValue().data().status()).isEqualTo("DONE");
        assertThat(captor.getValue().data().secret()).isEqualTo("secret-abc");
    }

    @Test
    void returns200EvenWhenServiceIgnoresTheEvent() throws Exception {
        // handleDepositWebhook은 처리 못 하는 상황(대상없음/secret불일치 등)도 예외 없이
        // 조용히 끝나므로(void, Mock 기본 no-op) 컨트롤러는 그대로 200을 반환해야 한다 —
        // 토스가 실패로 보고 불필요하게 재전송을 반복하지 않게 하려는 의도.
        mockMvc.perform(post("/webhooks/toss/deposit-callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\"PAYMENT_1\"}}"))
                .andExpect(status().isOk());
    }
}
