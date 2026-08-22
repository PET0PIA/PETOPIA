-- V45__fair_settlement.sql
-- 행사별 최종정산(플랫폼 ↔ 행사) 신규 테이블. 기존 `settlement`(업체별 정산, fair_id+business_id
-- UNIQUE)은 그대로 두고 건드리지 않는다 - 나중에 필요해질 수 있어 백엔드에 남겨두기로 함
-- (2026-08-22 결정). 이 테이블은 업체 구분 없이 행사 하나당 1행만 가진다: 그 행사에 참가한
-- 모든 업체의 완료된 참가비(VENDOR_FEE)를 합산해서 플랫폼이 행사(주최측)에 정산해주는 개념.
CREATE TABLE `fair_settlement` (
    `fair_settlement_id`   BIGINT       NOT NULL AUTO_INCREMENT,
    `fair_id`              BIGINT       NOT NULL COMMENT 'fairs.fair_id',
    `gross_amount`         BIGINT       NOT NULL COMMENT '그 행사의 COMPLETED VENDOR_FEE 전체 합계(모든 참가업체 합산)',
    `refund_amount`        BIGINT       NOT NULL COMMENT '위 결제들에 걸린 COMPLETED 환불 합계',
    `commission_rate`      DECIMAL(5,4) NOT NULL COMMENT '정산 시점 요율 스냅샷. 이후 요율이 바뀌어도 과거 정산은 불변',
    `commission_amount`    BIGINT       NOT NULL COMMENT '(gross_amount - refund_amount) x commission_rate',
    `net_amount`           BIGINT       NOT NULL COMMENT '행사 지급액(플랫폼→행사 정산금)',
    `status`               VARCHAR(20)  NOT NULL COMMENT 'PENDING / CONFIRMED / PAID',
    `needs_recalculation`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '계산 이후 소속 참가업체 결제 중 하나라도 환불이 들어와 재계산이 필요하면 TRUE(기존 settlement 테이블과 동일 패턴, V9 참고)',
    `paid_at`              DATETIME     NULL,
    `confirmed_at`         DATETIME     NULL,
    `confirmed_by_user_id` BIGINT       NULL COMMENT '확정한 관리자 users.user_id',
    `created_at`           DATETIME     NOT NULL,
    `updated_at`           DATETIME     NOT NULL,
    CONSTRAINT `PK_FAIR_SETTLEMENT`         PRIMARY KEY (`fair_settlement_id`),
    CONSTRAINT `UK_FAIR_SETTLEMENT_FAIR`    UNIQUE (`fair_id`),
    KEY `idx_fair_settlement_status_created` (`status`, `created_at`),
    KEY `idx_fair_settlement_confirmed_by`   (`confirmed_by_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 위 fair_settlement의 감사근거 상세 내역(기존 settlement_item과 동일 패턴, 완전히 별개 테이블 -
-- 결제 하나가 구 settlement_item/신 fair_settlement_item 양쪽에 동시에 들어가도 서로 무관하다).
CREATE TABLE `fair_settlement_item` (
    `fair_settlement_item_id` BIGINT NOT NULL AUTO_INCREMENT,
    `fair_settlement_id`      BIGINT NOT NULL COMMENT 'fair_settlement.fair_settlement_id',
    `payment_id`              BIGINT NOT NULL COMMENT 'payment.payment_id',
    `refund_id`               BIGINT NULL     COMMENT 'refund.refund_id. 환불이 있을 때만',
    `amount_included`         BIGINT NOT NULL COMMENT '이 건이 합계에 기여한 금액',
    CONSTRAINT `PK_FAIR_SETTLEMENT_ITEM`         PRIMARY KEY (`fair_settlement_item_id`),
    CONSTRAINT `UK_FAIR_SETTLEMENT_ITEM_PAYMENT` UNIQUE (`payment_id`),
    KEY `idx_fair_settlement_item_settlement` (`fair_settlement_id`),
    KEY `idx_fair_settlement_item_refund`     (`refund_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
