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
 * 토스페이먼츠 결제서버가 직접 호출하는 웹훅 전용 컨트롤러. /api/payments/**와 경로를
 * 분리해둔 이유: 그쪽은 우리 서비스 JWT가 있어야 하는데, 토스는 JWT를 가질 수 없다
 * (SecurityConfig에서 /webhooks/** 전체를 의도적으로 permitAll 처리 — 위조 방지는
 * PaymentService.handleTossDepositCallback 내부의 secret 대조로 한다).
 */
@RestController
@RequestMapping("/webhooks/toss")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    /**
     * 가상계좌 입금통지. 토스 문서 기준 10초 안에 200을 줘야 하는데 우리 쪽 처리는 DB UPDATE
     * 한두 번 수준이라 여유 있다. 위조/중복/무관한 이벤트는 서비스 계층이 조용히 무시하고
     * 예외를 던지지 않으므로 여기서도 항상 200으로 응답한다(에러를 주면 토스가 계속 재시도한다).
     */
    @PostMapping("/deposit-callback")
    public ResponseEntity<Void> handleDepositCallback(@RequestBody TossWebhookEvent event) {
        paymentService.handleTossDepositCallback(event);
        return ResponseEntity.ok().build();
    }
}
