-- 예약 생성 시 유료 약관 동의 이력을 남기고,
-- 한 사용자가 같은 행사에 활성 예약을 둘 이상 만들지 못하게 한다.
ALTER TABLE `reservations`
    ADD COLUMN `reservation_terms_version` VARCHAR(30) NULL
        COMMENT '유료 예약 시 동의한 취소·환불 약관 버전' AFTER `agreed_terms`,
    ADD COLUMN `reservation_terms_agreed_at` DATETIME NULL
        COMMENT '유료 예약 취소·환불 약관 동의 시각' AFTER `reservation_terms_version`,
    ADD COLUMN `active_key` VARCHAR(50) AS (
        CASE
            WHEN `status` IN ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN')
            THEN CONCAT(`user_id`, '_', `fair_id`)
            ELSE NULL
        END
    ) STORED COMMENT '활성 예약 중복 방지용 계산 컬럼',
    ADD CONSTRAINT `UK_RESERVATION_ACTIVE_USER_FAIR` UNIQUE (`active_key`);
