-- 대기열을 행사별로 켜고 끄고, 통과 인원을 조정하기 위한 정책 테이블.
--
-- 원래는 application.yaml 프로퍼티(petopia.waiting-room.enabled-fair-ids / active-limit)로
-- 시작했는데, 이 값들은 성격상 운영 중에 바뀐다. 오픈 직후 백엔드 지표를 보며 통과 인원을
-- 올리고 내려야 하는데 프로퍼티는 재배포나 최소한 재기동을 요구한다. 정작 조정이 필요한
-- 순간에 손을 못 대는 셈이라 DB로 옮긴다.
--
-- 운영일(fair_dates)이 아니라 행사(fairs) 단위다. 대기열은 "이 행사 예매창구에 몇 명을
-- 들여보낼지"를 정하는 것이고, 그 안에서 어느 날짜를 고르는지는 통과한 뒤의 문제다.
--
-- TTL(활성 슬롯 12분 / 티켓 30분)은 여기 두지 않는다. 결제 제한시간 10분과 맞물려 있어
-- 행사별로 다를 이유가 없고, 잘못 줄이면 결제 중인 사용자가 슬롯을 잃는다. 프로퍼티로 남긴다.

CREATE TABLE `waiting_room_policies` (
    `waiting_room_policy_id` BIGINT   NOT NULL AUTO_INCREMENT,
    `fair_id`                BIGINT   NOT NULL COMMENT 'fairs.fair_id. 행사별 1건',
    `enabled`                BOOLEAN  NOT NULL DEFAULT FALSE COMMENT '이 행사에 대기열을 적용할지',
    `active_limit`           INT      NOT NULL DEFAULT 1000
        COMMENT '동시에 백엔드로 통과시킬 인원. 곧 예약 API의 유입 상한',
    `updated_by`             BIGINT   NOT NULL COMMENT 'EVENT_ADMIN 또는 SUPER_ADMIN users.user_id',
    `version`                INT      NOT NULL DEFAULT 0 COMMENT '관리자 동시 수정 충돌 감지용',
    `created_at`             DATETIME NOT NULL,
    `updated_at`             DATETIME NOT NULL,
    CONSTRAINT `PK_WAITING_ROOM_POLICIES` PRIMARY KEY (`waiting_room_policy_id`),
    CONSTRAINT `UK_WAITING_ROOM_FAIR`     UNIQUE (`fair_id`),
    -- 0 이하로 내리면 아무도 통과하지 못해 예매가 완전히 멈춘다. 상한은 이 값을 넘겨봐야
    -- 커넥션 풀과 스레드가 먼저 무너지므로 실수 방지선으로 걸어둔다.
    CONSTRAINT `CK_WAITING_ROOM_ACTIVE_LIMIT` CHECK (`active_limit` BETWEEN 1 AND 100000),
    KEY `idx_waiting_room_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
