-- =========================================================
-- V12__fair_cancel_refund_targets.sql
-- 행사 취소 환불을 재시도 가능하게 만들기 위한 작업 테이블 추가
--
-- 기존에는 취소 승인 API 응답 안에서 결제 도메인 조회(getPayments)/환불(refund)을
-- 동기로 한 번만 시도했다 - 그 호출이 실패하면 취소 승인 자체는 이미 커밋된 뒤라
-- 되돌릴 수 없고, review()는 PENDING 신청만 검토할 수 있어서 같은 API로 환불을
-- 다시 트리거할 방법이 없었다(코드래빗 리뷰 지적).
--
-- 이 테이블은 "이 결제, 환불 처리해야 함"이라는 작업 하나를 표현한다. 취소된 행사의
-- 결제를 스케줄러가 주기적으로 다시 훑어 채워 넣고(enumerate), PENDING 상태인 작업을
-- 역시 스케줄러가 반복 처리한다 - 그래서 취소 승인 API 자체는 더 이상 결제 도메인
-- 호출 성패에 영향받지 않는다.
-- =========================================================

CREATE TABLE `fair_cancel_refund_targets` (
    `fair_cancel_refund_target_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `fair_id`                      BIGINT       NOT NULL COMMENT 'fairs.fair_id',
    `payment_id`                   BIGINT       NOT NULL COMMENT 'payment.payment_id(결제 도메인 소유)',
    `payment_type`                 VARCHAR(30)  NOT NULL COMMENT 'RESERVATION_DEPOSIT / VENDOR_FEE. 발견 시점 스냅샷',
    `status`                       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / COMPLETED / FAILED',
    `attempt_count`                INT          NOT NULL DEFAULT 0 COMMENT '처리 시도 횟수',
    `last_error`                   VARCHAR(500) NULL     COMMENT '마지막 실패 사유(관찰용)',
    `created_at`                   DATETIME     NOT NULL,
    `updated_at`                   DATETIME     NOT NULL,
    CONSTRAINT `PK_FAIR_CANCEL_REFUND_TARGETS`         PRIMARY KEY (`fair_cancel_refund_target_id`),
    CONSTRAINT `UK_FAIR_CANCEL_REFUND_TARGETS_PAYMENT`  UNIQUE (`payment_id`),
    KEY `idx_fair_cancel_refund_targets_fair`   (`fair_id`),
    KEY `idx_fair_cancel_refund_targets_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
