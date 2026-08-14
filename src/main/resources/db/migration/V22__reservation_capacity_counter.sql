-- 정원 점유 수를 COUNT(*)가 아니라 물리 컬럼으로 들고 O(1)로 판정한다.
--
-- 기존 예약 생성은 fair_dates 행을 FOR UPDATE로 잠근 뒤 reservations를 COUNT(*)로 세어
-- 정원을 확인했다. 이 방식은 같은 운영일 요청을 전부 직렬화하는 데다, 임계구간 안의
-- COUNT(*)가 예약 건수에 비례해 무거워져 "팔릴수록 느려지는" 특성이 있었다.
--
-- 이 컬럼이 생기면 정원 판정을 조건부 UPDATE 한 문장으로 옮길 수 있다.
--   UPDATE fair_dates SET reserved_count = reserved_count + 1
--    WHERE ... AND reserved_count < capacity
-- WHERE 평가와 SET이 같은 문장이라 그 사이에 다른 트랜잭션이 끼어들 수 없어,
-- 애플리케이션 잠금 없이도 정원 초과가 발생하지 않는다.
--
-- 이 마이그레이션은 컬럼만 추가한다. 이 시점에는 아무 코드도 이 컬럼을 읽지 않으므로
-- 단독 배포해도 무해하고, 뒤따르는 코드 배포를 롤백해도 컬럼은 남아 있어 안전하다.

ALTER TABLE `fair_dates`
    ADD COLUMN `reserved_count` INT NOT NULL DEFAULT 0
        COMMENT '정원을 점유 중인 사전예약(ADVANCE) 수' AFTER `capacity`;

-- 기존 데이터 백필. 점유 판정 기준은 애플리케이션과 정확히 같게 맞춘다.
-- 현장예매(ONSITE_DIRECT)는 기존에도 정원 대상이 아니었으므로 여기서도 제외한다.
UPDATE `fair_dates` fd
   SET fd.`reserved_count` = (
        SELECT COUNT(*)
          FROM `reservations` r
         WHERE r.`fair_id` = fd.`fair_id`
           AND r.`visit_date` = fd.`operation_date`
           AND r.`reservation_type` = 'ADVANCE'
           AND r.`status` IN ('PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN')
   );

-- 반납(release)이 음수로 내려가지 않게 하는 최후 방어선.
-- 애플리케이션도 `WHERE reserved_count > 0`으로 한 번 막지만, 운영 중 수동 SQL로
-- 카운터를 건드리는 일이 실제로 일어나므로 DB에도 걸어둔다.
-- MySQL 8.0.16+ 에서 실제로 강제된다.
ALTER TABLE `fair_dates`
    ADD CONSTRAINT `CK_FAIR_DATE_RESERVED_COUNT` CHECK (`reserved_count` >= 0);
