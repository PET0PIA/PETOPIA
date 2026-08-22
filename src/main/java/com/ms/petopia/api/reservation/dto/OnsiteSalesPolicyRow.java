package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class OnsiteSalesPolicyRow {
    private Long onsiteSalesPolicyId;
    private Long fairDateId;
    private long price;
    /** 현장예매 전용 정원. null이면 제한 없음(V49). */
    private Integer capacity;
    /** 정원을 점유 중인 현장예매 수. 관리자가 직접 고치는 값이 아니라 판매·취소가 움직인다. */
    private int reservedCount;
    private String status;
    private Long updatedBy;
    private int version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
