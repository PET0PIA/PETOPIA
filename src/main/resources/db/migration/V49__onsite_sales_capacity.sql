-- 현장예매 전용 정원을 운영일별 현장 판매 정책에 붙인다.
--
-- 지금까지 현장예매는 정원 대상이 아니었다(V22 참고). fair_dates.capacity/reserved_count는
-- 사전예약만 움직였고, 현장예매는 판매 상태(OPEN/PAUSED/CLOSED)로만 조절할 수 있었다.
-- 여기서는 사전예약 정원을 나눠 쓰는 대신 현장 몫을 따로 둔다 - 사전예약이 다 팔려도
-- 현장에서 팔 자리가 남아야 한다는 운영 요구 때문이다.
--
-- capacity 는 NULL 을 허용한다. NULL = 제한 없음(지금까지의 동작)이라, 이 마이그레이션만
-- 단독 배포해도 기존 행사의 현장예매 동작은 하나도 바뀌지 않는다. 관리자가 숫자를 넣은
-- 운영일부터 제한이 걸린다. 0 을 무제한으로 쓰지 않은 이유는 "0석(판매 중단)"과 구분하기
-- 위해서다.

ALTER TABLE `onsite_sales_policies`
    ADD COLUMN `capacity` INT NULL
        COMMENT '현장예매 전용 정원. NULL이면 제한 없음' AFTER `price`,
    ADD COLUMN `reserved_count` INT NOT NULL DEFAULT 0
        COMMENT '정원을 점유 중인 현장예매(ONSITE_DIRECT) 수' AFTER `capacity`,
    ADD CONSTRAINT `CK_ONSITE_SALES_CAPACITY` CHECK (`capacity` IS NULL OR `capacity` >= 0),
    ADD CONSTRAINT `CK_ONSITE_SALES_RESERVED_COUNT` CHECK (`reserved_count` >= 0);

-- 기존 데이터 백필. 점유 판정 기준(상태 집합)은 사전예약(V22)과 정확히 같게 맞춘다.
-- 다른 기준을 쓰면 같은 "점유 중"이 도메인마다 다른 뜻이 되어 대조가 불가능해진다.
UPDATE `onsite_sales_policies` osp
  JOIN `fair_dates` fd ON fd.`fair_date_id` = osp.`fair_date_id`
   SET osp.`reserved_count` = (
        SELECT COUNT(*)
          FROM `reservations` r
         WHERE r.`fair_id` = fd.`fair_id`
           AND r.`visit_date` = fd.`operation_date`
           AND r.`reservation_type` = 'ONSITE_DIRECT'
           AND r.`status` IN ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN')
   );
