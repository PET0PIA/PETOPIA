package com.ms.petopia.api.reservation.dto;

/**
 * 현장예매 정책 저장 요청.
 *
 * @param capacity 현장예매 전용 정원. null이면 "제한 없음"으로 저장한다 - 정원을 걸지 않는
 *                 것이 기본값이라, 값을 보내지 않은 오래된 클라이언트도 기존처럼 동작한다.
 */
public record UpdateOnsiteSalesPolicyRequest(
        Long price,
        Integer capacity,
        String status,
        Integer expectedVersion
) {
}
