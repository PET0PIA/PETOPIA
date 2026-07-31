-- 현장 직접예매 운영 정책과 예약/입장 상태 전이를 예약·입장 도메인에서 관리한다.
-- 결제 상세와 PG 연동 데이터는 결제 도메인 소유이므로 이 마이그레이션에서 변경하지 않는다.

CREATE TABLE `onsite_sales_policies` (
    `onsite_sales_policy_id` BIGINT      NOT NULL AUTO_INCREMENT,
    `fair_date_id`            BIGINT      NOT NULL COMMENT 'fair_dates.fair_date_id. 운영일별 1건',
    `price`                   BIGINT      NOT NULL DEFAULT 0 COMMENT '현장 직접예매 가격 스냅샷의 원본 값',
    `status`                  VARCHAR(20) NOT NULL DEFAULT 'CLOSED' COMMENT 'CLOSED / OPEN / PAUSED',
    `updated_by`              BIGINT      NOT NULL COMMENT 'EVENT_ADMIN 또는 SUPER_ADMIN users.user_id',
    `version`                 INT         NOT NULL DEFAULT 0 COMMENT '관리자 동시 수정 충돌 감지용',
    `created_at`              DATETIME    NOT NULL,
    `updated_at`              DATETIME    NOT NULL,
    CONSTRAINT `PK_ONSITE_SALES_POLICIES` PRIMARY KEY (`onsite_sales_policy_id`),
    CONSTRAINT `UK_ONSITE_SALES_FAIR_DATE` UNIQUE (`fair_date_id`),
    CONSTRAINT `CK_ONSITE_SALES_PRICE` CHECK (`price` >= 0),
    CONSTRAINT `CK_ONSITE_SALES_STATUS` CHECK (`status` IN ('CLOSED', 'OPEN', 'PAUSED')),
    KEY `idx_onsite_sales_status_updated` (`status`, `updated_at`),
    KEY `idx_onsite_sales_updated_by` (`updated_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


ALTER TABLE `reservations`
    ADD COLUMN `reservation_type` VARCHAR(20) NOT NULL DEFAULT 'ADVANCE'
        COMMENT 'ADVANCE / ONSITE_DIRECT' AFTER `visit_date`,
    ADD COLUMN `payment_expires_at` DATETIME NULL
        COMMENT '유료 예약 결제 제한시각. 생성 시각부터 10분' AFTER `reserved_at`,
    ADD CONSTRAINT `CK_RESERVATION_TYPE`
        CHECK (`reservation_type` IN ('ADVANCE', 'ONSITE_DIRECT')),
    ADD KEY `idx_reservation_pending_expiry` (`status`, `payment_expires_at`),
    ADD KEY `idx_reservation_type_fair_visit` (`reservation_type`, `fair_id`, `visit_date`);


-- 결제 도메인으로부터 받은 성공 통지를 예약 도메인에서 멱등 처리하기 위한 최소 영수증이다.
-- 결제수단, 제공자 응답, 결제 상태 등 결제 상세를 복제하지 않는다.
CREATE TABLE `reservation_payment_confirmations` (
    `confirmation_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `event_id`        VARCHAR(100) NOT NULL COMMENT '결제 도메인이 부여한 멱등 이벤트 ID',
    `payment_id`      BIGINT       NOT NULL COMMENT '결제 도메인의 로컬 결제 ID. FK를 걸지 않는다',
    `reservation_id`  BIGINT       NOT NULL COMMENT 'reservations.reservation_id. 성공 결제는 예약당 1건',
    `paid_amount`     BIGINT       NOT NULL,
    `paid_at`         DATETIME     NOT NULL,
    `received_at`     DATETIME     NOT NULL,
    CONSTRAINT `PK_RESERVATION_PAYMENT_CONFIRMATIONS` PRIMARY KEY (`confirmation_id`),
    CONSTRAINT `UK_RESERVATION_PAYMENT_EVENT` UNIQUE (`event_id`),
    CONSTRAINT `UK_RESERVATION_PAYMENT_RESERVATION` UNIQUE (`reservation_id`),
    CONSTRAINT `UK_RESERVATION_PAYMENT_PAYMENT` UNIQUE (`payment_id`),
    CONSTRAINT `CK_RESERVATION_PAYMENT_AMOUNT` CHECK (`paid_amount` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


ALTER TABLE `reservation_histories`
    MODIFY COLUMN `changed_by` BIGINT NULL COMMENT '사용자 처리면 users.user_id, 시스템/결제 통지면 NULL',
    ADD COLUMN `actor_type` VARCHAR(20) NOT NULL DEFAULT 'USER'
        COMMENT 'USER / ADMIN / SYSTEM / PAYMENT' AFTER `changed_by`,
    ADD CONSTRAINT `CK_RESERVATION_HISTORY_ACTOR_TYPE`
        CHECK (`actor_type` IN ('USER', 'ADMIN', 'SYSTEM', 'PAYMENT'));


-- QR 유효성은 QR 상태가 아니라 예약 상태와 사용 가능 시간으로 판정한다.
-- V1 호환을 위해 컬럼 자체는 남기되 신규 데이터에서는 NULL을 허용한다.
ALTER TABLE `entry_qrs`
    MODIFY COLUMN `qr_status` VARCHAR(20) NULL
        COMMENT '사용하지 않음. 예약 상태와 available_from/expires_at으로 유효성 판정';


ALTER TABLE `entry_records`
    ADD COLUMN `entry_source` VARCHAR(20) NOT NULL DEFAULT 'ADVANCE'
        COMMENT 'ADVANCE / ONSITE_DIRECT / KIOSK' AFTER `user_id`,
    ADD CONSTRAINT `CK_ENTRY_RECORD_SOURCE`
        CHECK (`entry_source` IN ('ADVANCE', 'ONSITE_DIRECT', 'KIOSK')),
    ADD KEY `idx_entry_record_fair_source_first`
        (`fair_id`, `entry_source`, `first_checked_in_at`);
