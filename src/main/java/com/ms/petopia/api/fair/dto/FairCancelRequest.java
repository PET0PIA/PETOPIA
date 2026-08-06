package com.ms.petopia.api.fair.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * fair_cancel_requests 테이블 매핑 객체. 행사(fairs) 하위, EVENT_ADMIN이 올린 취소 신청과
 * SUPER_ADMIN의 승인/반려 이력을 보유한다.
 *
 * <p>승인되면 이 테이블의 status만 바뀌는 게 아니라 {@code fairs.canceled_at}도 함께 채워진다
 * ({@link FairCancelRequestService#review} 참고) - reservation/recruitnotice 도메인은 이미
 * {@code fairs.canceled_at IS NOT NULL}을 취소 신호로 보고 있어서, 실제 취소 여부는 이 테이블이
 * 아니라 fairs.canceled_at이 source of truth다. 이 테이블은 신청/심사 이력 보관용이다.
 *
 * <p>record가 아닌 이유는 {@link Fair}와 동일 - MyBatis 세터 기반 매핑.
 */
@Getter
@Setter
@ToString
public class FairCancelRequest {

    private Long fairCancelRequestId;

    /** fairs.fair_id */
    private Long fairId;

    /** 신청한 EVENT_ADMIN - users.user_id */
    private Long requestedBy;

    private String reason;

    private FairCancelRequestStatus status;

    private String rejectReason;

    /** 심사한 SUPER_ADMIN - users.user_id */
    private Long reviewedBy;
    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;
}
