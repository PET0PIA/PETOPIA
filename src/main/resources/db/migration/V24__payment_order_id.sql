-- 토스 승인에 쓰는 주문번호를 컬럼으로 들고, 결제 시도마다 새로 발급한다.
--
-- 기존에는 주문번호를 저장하지 않고 "PAYMENT_" + payment_id 로 그때그때 파생시켰다.
-- 결제 행은 예약 1건당 1개로 고정이고(UK_PAYMENT_IDEMPOTENCY_KEY), 실패한 결제는
-- 그 행을 PENDING으로 되돌려 재사용하므로 payment_id가 바뀌지 않는다.
--   → 재시도할 때마다 주문번호가 그대로였다.
--
-- 토스에서 orderId는 "결제 행"이 아니라 "승인 시도" 단위 식별자다. 사용자가 결제창에서
-- 승인까지 갔는데 우리 쪽 confirm이 실패한 경우, 그 주문번호에는 이미 승인 건이 잡혀 있다.
-- 그 상태에서 재시도하면 같은 주문번호로 다시 승인을 요청하게 되어 토스가 거부하고,
-- 한 번 이 상태에 빠진 예약은 재시도할수록 같은 벽에 부딪혀 영구히 결제가 불가능해진다.
--
-- 주문번호를 저장하는 이유는 값 자체가 UUID여서가 아니다. 프론트가 결제창에 넘긴
-- 주문번호와 서버가 승인 요청에 쓰는 주문번호가 반드시 같은 값이어야 하는데,
-- 양쪽에서 각자 만들면 시도마다 값이 갈린다. 한 번 발급해 저장하고 둘 다 그걸 읽는다.

-- 1단계: 기존 행이 있으므로 우선 NULL 허용으로 추가한다.
ALTER TABLE `payment`
    ADD COLUMN `order_id` VARCHAR(64) NULL
        COMMENT '토스 승인용 주문번호. 결제 시도마다 새로 발급한다' AFTER `idempotency_key`;

-- 2단계: 이미 발급된 주문번호를 그대로 보존한다.
-- 배포 시점에 결제창이 떠 있는 건이 있을 수 있다. 그 건들은 프론트가 이미
-- "PAYMENT_{payment_id}"를 들고 있으므로, 값을 바꾸면 승인 단계에서 어긋난다.
UPDATE `payment`
   SET `order_id` = CONCAT('PAYMENT_', `payment_id`)
 WHERE `order_id` IS NULL;

-- 3단계: 이제 필수이자 유일하다.
-- UNIQUE가 "한 주문번호는 한 번만 쓴다"는 규칙의 최종 방어선이다. 발급 로직에 문제가
-- 생겨도 같은 값이 두 번 저장되지는 않는다.
ALTER TABLE `payment`
    MODIFY COLUMN `order_id` VARCHAR(64) NOT NULL
        COMMENT '토스 승인용 주문번호. 결제 시도마다 새로 발급한다',
    ADD CONSTRAINT `UK_PAYMENT_ORDER_ID` UNIQUE (`order_id`);
