package com.ms.petopia.api.payment.controller;

import com.ms.petopia.api.payment.dto.ConfirmPaymentRequest;
import com.ms.petopia.api.payment.dto.PaymentListResponse;
import com.ms.petopia.api.payment.service.PaymentService;
import com.ms.petopia.api.payment.dto.PaymentResponse;
import com.ms.petopia.api.payment.dto.VendorFeePaymentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    // 결제 단건 상세 조회. paymentId로 PaymentRow를 그대로 조회해서 반환한다.
    @GetMapping("/payments/{paymentId}")
    public PaymentResponse getPayment(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal Long userId
            ) {
        // TODO 인증 도메인 완성 후: 조회한 결제가 이 userId 소유(또는 관리자 권한)인지
        // 검증하는 로직 추가. 지금은 헤더 존재를 강제하는 수준까지만
        // (다른 도메인 컨트롤러들과 최소한의 관례만 맞춘 것, 완전한 IDOR 방지는 아님).
        return paymentService.getPayment(paymentId);
    }

    // 예약ID로 그 예약의 예약금 결제 조회. 예약 도메인이 취소 처리 중 환불 API(paymentId 기준)를
    // 부르기 전에 paymentId를 알아내는 용도.
    @GetMapping("/reservations/{reservationId}/payment")
    public PaymentResponse getPaymentByReservation(@PathVariable Long reservationId) {
        return paymentService.getByReservationId(reservationId);
    }

    // 참가비 결제 생성. application 테이블은 조회하지 않고, 요청 바디로 받은
    // 금액을 그대로 신뢰해서 PENDING 상태 결제 건을 만든다(결제 승인은 별도 confirm 호출).
    @PostMapping("/vendor-applications/{applicationId}/payment")
    public ResponseEntity<PaymentResponse>payVendorFee(
            @PathVariable Long applicationId,
            @AuthenticationPrincipal Long userId,
            @Valid
            @RequestBody VendorFeePaymentRequest request
            ) {
        // 조회(GET)는 그냥 객체를 리턴해도 스프링이 200 OK로 응답하지만,
        // "새로 만들었다"는 의미를 명확히 하려고 리소스 생성 성공은
        // 관례적으로 201 Created를 씀. 그래서 ResponseEntity로 감싸서 상태코드 직접 지정.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.payVendorFee(applicationId,userId, request));

    }

    // 결제 승인 확정. PENDING 건을 선점(PROCESSING)한 뒤 토스 confirm API를 호출해서
    // 실제 승인 여부를 확인하고, 성공 시 COMPLETED로 반영한다(예약금 결제면 승인 직후
    // 예약 도메인에도 완료 통지까지 보낸다).
    @PostMapping("/payments/{paymentId}/confirm")
    public PaymentResponse confirmPayment(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ConfirmPaymentRequest request
    ) {
        return paymentService.confirmPayment(paymentId, userId, request);
    }

    // 예약금 결제 생성. 참가비와 달리 금액을 클라이언트가 안 보내고, 예약 도메인의
    // 내부 계약 API(getPaymentContext)로 진짜 금액/소유자를 조회해서 그 값으로 PENDING 건을 만든다.
    @PostMapping("/reservations/{reservationId}/payment")
    public ResponseEntity<PaymentResponse> payReservationDeposit(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.payReservationDeposit(reservationId, userId));
    }

    // 행사개설비 결제 생성. 예약금과 동일하게 요청 바디가 없다 - 금액은 행사 도메인의 내부
    // 계약(FairOpeningFeePaymentContractClient)에서 승인 시 확정된 값을 조회해 쓴다
    // (승인은 참가비와 동일하게 별도 confirm 호출).
    @PostMapping("/fairs/{fairId}/opening-payment")
    public ResponseEntity<PaymentResponse> payFairOpeningFee(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.payFairOpeningFee(fairId, userId));
    }

    // 조건별 결제 목록(관리자용). fairId·businessId·paymentType·status 전부 선택적 필터.
    // page/size 범위 검증은 여기 어노테이션이 아니라 PaymentService에서 한다 — standaloneSetup
    // 기반 컨트롤러 테스트에서 메서드 파라미터 검증(@Min/@Max)이 실제로 안 걸리는 걸 확인해서
    // (CodeRabbit 리뷰 지적, PR #62), 프레임워크 동작에 기대지 않기로 함.
    @GetMapping("/payments")
    public PaymentListResponse getPayments(
            @RequestParam(required = false) Long fairId,
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // TODO 인증 도메인 완성 후 event_admin(담당행사)/super_admin 권한 검증 추가
        return paymentService.getPayments(fairId, businessId, paymentType, status, page, size);
    }

    // 로그인 사용자 본인의 결제 내역(마이페이지). page/size 검증은 위와 동일하게 서비스 계층에서.
    @GetMapping("/me/payments")
    public PaymentListResponse getMyPayments(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return paymentService.getMyPayments(userId, page, size);
    }

}
