package com.ms.petopia.api.payment.controller;

import com.ms.petopia.api.payment.service.PaymentService;
import com.ms.petopia.api.payment.dto.PaymentResponse;
import com.ms.petopia.api.payment.dto.VendorFeePaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// @RestController = @Controller + @ResponseBody 합친 것.
// 메서드가 리턴하는 객체(record 등)를 View(html)로 안 넘기고
// 바로 JSON으로 직렬화해서 응답 바디에 넣어줌.
@RestController
// 이 컨트롤러의 모든 엔드포인트 앞에 공통으로 붙는 경로.
// 아래 메서드들의 "/payments/..."가 실제로는 "/api/payments/..."가 됨.
@RequestMapping("/api")
// PaymentService를 필드로 받는 생성자를 롬복이 자동으로 만들어줌
// (= 스프링이 PaymentService 빈을 여기 주입해줌, 의존성 주입)
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/payments/{paymentId}")
    public PaymentResponse getPayment(@PathVariable Long paymentId) {
        return paymentService.getPayment(paymentId);
    }

    @PostMapping("/vendor-applications/{applicationId}/payment")
    public ResponseEntity<PaymentResponse>payVendorFee(
            @PathVariable Long applicationId,
            @RequestBody VendorFeePaymentRequest request
            ) {
        // 조회(GET)는 그냥 객체를 리턴해도 스프링이 200 OK로 응답하지만,
        // "새로 만들었다"는 의미를 명확히 하려고 리소스 생성 성공은
        // 관례적으로 201 Created를 씀. 그래서 ResponseEntity로 감싸서 상태코드 직접 지정.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.payVendorFee(applicationId, request));

    }



}
