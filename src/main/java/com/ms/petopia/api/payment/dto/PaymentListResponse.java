package com.ms.petopia.api.payment.dto;

import java.util.List;

/**
 * 결제 목록 조회 공통 응답 포맷(API 명세서 "공통 규칙 > 목록 응답 포맷" 참고).
 * {@code GET /api/payments}(관리자용 필터 조회)와 {@code GET /api/me/payments}(마이페이지)가
 * 같이 쓴다.
 */
public record PaymentListResponse(
        List<PaymentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static PaymentListResponse of(List<PaymentRow> rows, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PaymentListResponse(
                rows.stream().map(PaymentResponse::from).toList(),
                page, size, totalElements, totalPages
        );
    }
}
