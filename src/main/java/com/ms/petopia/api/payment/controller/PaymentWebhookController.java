package com.ms.petopia.api.payment.controller;

import com.ms.petopia.api.payment.dto.TossWebhookEvent;
import com.ms.petopia.api.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스페이먼츠가 사용자 JWT 없이 직접 호출하는 웹훅 전용 컨트롤러. 사용자 API(/api/**)와
 * 경로 자체를 분리해서(/webhooks/toss/**) SecurityConfig 인증 규칙을 명확히 구분한다
 * (permitAll — 위조 방지는 요청 헤더가 아니라 PaymentService.handleDepositWebhook의
 * secret 대조가 담당).
 */
@RestController
@RequestMapping("/webhooks/toss")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    // 대상 없음/secret 불일치 등 처리 못 하는 상황도 예외 없이 조용히 끝나므로 항상 200을
    // 반환한다 — 실패 응답을 주면 토스가 실패로 보고 불필요하게 재전송을 반복한다.
    @PostMapping("/deposit-callback")
    public ResponseEntity<Void> handleDepositCallback(@RequestBody TossWebhookEvent event) {
        paymentService.handleDepositWebhook(event);
        return ResponseEntity.ok().build();
    }
}
