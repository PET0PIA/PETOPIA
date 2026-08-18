-- 활성 예약 중복 제한을 '행사당 1개'에서 '행사+방문일당 1개'로 완화한다.
-- 같은 사용자가 같은 행사의 서로 다른 방문일을 각각 예약할 수 있게 하되,
-- 같은 행사·같은 방문일에는 여전히 활성 예약을 하나만 허용한다.
-- (기존 UK_RESERVATION_ACTIVE_USER_FAIR는 V3에서 추가했다. V3 파일은 수정하지 않고 여기서 재정의한다.)

-- 계산 컬럼에 걸린 유니크 제약을 먼저 제거한 뒤 컬럼 자체를 지운다.
ALTER TABLE `reservations`
    DROP INDEX `UK_RESERVATION_ACTIVE_USER_FAIR`,
    DROP COLUMN `active_key`;

-- 방문일까지 포함한 새 계산 컬럼과 유니크 제약을 다시 만든다.
ALTER TABLE `reservations`
    ADD COLUMN `active_key` VARCHAR(60) AS (
        CASE
            WHEN `status` IN ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN')
            THEN CONCAT(`user_id`, '_', `fair_id`, '_', `visit_date`)
            ELSE NULL
        END
    ) STORED COMMENT '활성 예약 중복 방지용 계산 컬럼(사용자·행사·방문일 단위)',
    ADD CONSTRAINT `UK_RESERVATION_ACTIVE_USER_FAIR_DATE` UNIQUE (`active_key`);
