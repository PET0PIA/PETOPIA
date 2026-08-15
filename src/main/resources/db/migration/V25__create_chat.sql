-- =========================================================
-- V25__create_chat.sql
-- 상담 챗봇(채널톡형 위젯) 기본 스키마
-- =========================================================
--
-- 설계 메모
--
-- 1) 문의 유형 버튼을 코드가 아니라 테이블로 둔다.
--    버튼 문구·순서·활성 여부는 운영 중에 자주 바뀌는 값이라, 바뀔 때마다 배포하지
--    않으려면 데이터여야 한다. answer_type으로 "고정 답변 / AI 답변 / 상담사 연결"을
--    구분하고, AI 유형이 참고할 도메인 지식(ai_context)도 같은 이유로 컬럼에 둔다.
--
-- 2) 대화의 잠금은 status 하나로 표현한다.
--    "AI가 1회 답한 뒤에는 상담사가 답하기 전까지 사용자가 더 질문할 수 없다"는 요구는
--    프론트의 입력창 비활성화로 구현하면 안 된다(요청을 직접 만들면 뚫린다). 서버가
--    status를 단일 판정 주체로 삼고, 사용자 메시지 전송은 아래 조건부 UPDATE로만 통과시킨다.
--
--      UPDATE chat_conversation SET status='WAITING_AGENT'
--       WHERE conversation_id=? AND status IN ('BOT','IN_PROGRESS')
--
--    affected=0이면 이미 상담사 답변 대기 중이거나 종료된 대화다 → 거부(CH010/CH011).
--    예약 정원·대기열에서 쓰던 낙관적 동시성 방식과 같다.
--
-- 3) ai_answer_count는 카운터지 플래그가 아니다.
--    "대화당 1회"라는 한도를 나중에 2회·3회로 바꿀 여지를 남긴다. 그리고 AI 호출 전에
--    이 값을 조건부 UPDATE로 선점해야(= 0인 행만 1로 올리기) 두 요청이 동시에 들어와도
--    Claude를 두 번 부르지 않는다. 호출이 실패하면 되돌린다(대기열 정원 반납과 같은 패턴).
--
-- 4) 게스트 식별자(guest_key)를 두는 이유.
--    상담은 비로그인 상태에서 시작되는 경우가 대부분이다. user_id가 없을 때 대화 소유자를
--    가리킬 값이 필요하고, 추측 가능한 값이면 남의 상담을 열람할 수 있으므로 UUIDv4를
--    서버가 발급한다. 조회·전송 시 이 값이 일치하는지 반드시 검증한다.
--
-- 5) 타이핑 표시는 여기에 없다.
--    상담사 입력 중 신호는 상담 1건당 수십~수백 번 발생하는 휘발성 데이터다. DB에 쓸
--    가치가 없고 쓰면 안 된다. Redis 단기 TTL 키로만 다룬다(후속 작업).

-- ---------------------------------------------------------
-- 문의 유형 버튼
-- ---------------------------------------------------------
CREATE TABLE `chat_menu`
(
    `menu_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '문의 유형 ID',
    `parent_menu_id` BIGINT       NULL COMMENT '상위 유형(2단계 메뉴용). NULL이면 최상위',
    `code`           VARCHAR(40)  NOT NULL COMMENT '프론트/시딩이 참조하는 불변 코드',
    `label`          VARCHAR(100) NOT NULL COMMENT '버튼에 노출되는 문구',
    `display_order`  INT          NOT NULL DEFAULT 0 COMMENT '노출 순서 (오름차순)',
    `answer_type`    VARCHAR(10)  NOT NULL COMMENT 'FIXED | AI | AGENT',
    `fixed_answer`   TEXT         NULL COMMENT 'FIXED 전용 답변 본문',
    `ai_context`     TEXT         NULL COMMENT 'AI 전용. 프롬프트에 주입할 도메인 지식',
    `is_active`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '노출 여부',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (`menu_id`),
    CONSTRAINT `uk_chat_menu_code` UNIQUE (`code`),
    CONSTRAINT `fk_chat_menu_parent` FOREIGN KEY (`parent_menu_id`) REFERENCES `chat_menu` (`menu_id`),
    -- 활성 메뉴를 순서대로 읽는 게 유일한 조회 패턴이다.
    INDEX `idx_chat_menu_active_order` (`is_active`, `display_order`)
) COMMENT = '상담 챗봇 문의 유형 버튼';

-- ---------------------------------------------------------
-- 대화
-- ---------------------------------------------------------
CREATE TABLE `chat_conversation`
(
    `conversation_id`   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '대화 ID',
    `user_id`           BIGINT      NULL COMMENT '로그인 사용자. 게스트로 시작해 로그인하면 승계된다',
    `guest_key`         CHAR(36)    NULL COMMENT '비로그인 소유자 식별자(UUIDv4). 서버가 발급한다',
    `menu_id`           BIGINT      NULL COMMENT '진입할 때 선택한 문의 유형',
    -- fair_id는 지금 쓰지 않지만 NULL 허용으로 미리 둔다. 박람회 관리자에게도 상담 답변
    -- 권한을 주기로 하면 "자기 박람회 문의만" 스코프를 걸 축이 필요한데, 그때 가서
    -- 컬럼을 추가하면 이미 쌓인 대화는 소급 분류가 불가능하다.
    `fair_id`           BIGINT      NULL COMMENT '문의가 특정 행사에 속하는 경우',
    `status`            VARCHAR(20) NOT NULL DEFAULT 'BOT' COMMENT 'BOT | WAITING_AGENT | IN_PROGRESS | CLOSED',
    `ai_answer_count`   INT         NOT NULL DEFAULT 0 COMMENT 'AI가 답한 횟수. 호출 전 조건부 UPDATE로 선점한다',
    `assigned_admin_id` BIGINT      NULL COMMENT '배정된 상담사 user_id',
    `last_message_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '마지막 메시지 시각. 대기열 정렬 기준',
    `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    `closed_at`         DATETIME    NULL COMMENT '종료일시',
    PRIMARY KEY (`conversation_id`),
    CONSTRAINT `fk_chat_conversation_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT `fk_chat_conversation_menu` FOREIGN KEY (`menu_id`) REFERENCES `chat_menu` (`menu_id`),
    CONSTRAINT `fk_chat_conversation_fair` FOREIGN KEY (`fair_id`) REFERENCES `fairs` (`fair_id`),
    CONSTRAINT `fk_chat_conversation_admin` FOREIGN KEY (`assigned_admin_id`) REFERENCES `users` (`user_id`),
    -- 소유자가 둘 다 없으면 누구의 상담인지 영원히 알 수 없다. 애플리케이션 버그가
    -- 그런 행을 만들지 못하도록 DB에서 막는다.
    CONSTRAINT `ck_chat_conversation_owner` CHECK (`user_id` IS NOT NULL OR `guest_key` IS NOT NULL),
    -- 관리자 대기열: 상태로 거르고 오래 기다린 순으로 정렬한다.
    INDEX `idx_chat_conversation_status_time` (`status`, `last_message_at`),
    -- 위젯 재진입: 이 사용자의 진행 중인 대화를 찾는다.
    INDEX `idx_chat_conversation_guest` (`guest_key`),
    INDEX `idx_chat_conversation_user` (`user_id`)
) COMMENT = '상담 챗봇 대화';

-- ---------------------------------------------------------
-- 메시지
-- ---------------------------------------------------------
CREATE TABLE `chat_message`
(
    `message_id`      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '메시지 ID',
    `conversation_id` BIGINT      NOT NULL COMMENT '대화 ID',
    `sender_type`     VARCHAR(10) NOT NULL COMMENT 'USER | BOT | AI | AGENT | SYSTEM',
    `sender_id`       BIGINT      NULL COMMENT 'USER면 user_id, AGENT면 상담사 user_id. 봇/AI는 NULL',
    `menu_id`         BIGINT      NULL COMMENT '버튼 클릭으로 생성된 메시지의 원본 유형',
    `content`         TEXT        NOT NULL COMMENT '메시지 본문',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    PRIMARY KEY (`message_id`),
    CONSTRAINT `fk_chat_message_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `chat_conversation` (`conversation_id`),
    CONSTRAINT `fk_chat_message_menu` FOREIGN KEY (`menu_id`) REFERENCES `chat_menu` (`menu_id`),
    -- 조회는 전부 "이 대화의 message_id > N" 형태다(커서 페이징). PK를 커서로 쓰므로
    -- 정렬 컬럼을 따로 두지 않는다 - created_at은 같은 초에 여러 건이 들어와 커서로 부적합하다.
    INDEX `idx_chat_message_cursor` (`conversation_id`, `message_id`)
) COMMENT = '상담 챗봇 메시지';

-- ---------------------------------------------------------
-- 운영시간 / 휴무일
-- ---------------------------------------------------------
CREATE TABLE `chat_business_hour`
(
    `business_hour_id` BIGINT     NOT NULL AUTO_INCREMENT COMMENT 'PK',
    -- java.time.DayOfWeek와 맞춘다(1=월 ... 7=일). Calendar/JS의 0=일 규칙과 섞이면
    -- 하루씩 밀린 채로 몇 주간 아무도 눈치채지 못하는 종류의 버그가 된다.
    `day_of_week`      TINYINT    NOT NULL COMMENT '1=월 ... 7=일 (java.time.DayOfWeek)',
    `start_time`       TIME       NOT NULL COMMENT '상담 시작 시각',
    `end_time`         TIME       NOT NULL COMMENT '상담 종료 시각',
    `is_active`        TINYINT(1) NOT NULL DEFAULT 1 COMMENT '적용 여부',
    PRIMARY KEY (`business_hour_id`),
    CONSTRAINT `uk_chat_business_hour_day` UNIQUE (`day_of_week`),
    CONSTRAINT `ck_chat_business_hour_range` CHECK (`start_time` < `end_time`),
    CONSTRAINT `ck_chat_business_hour_dow` CHECK (`day_of_week` BETWEEN 1 AND 7)
) COMMENT = '상담사 운영시간';

CREATE TABLE `chat_holiday`
(
    `holiday_date` DATE         NOT NULL COMMENT '휴무일',
    `reason`       VARCHAR(100) NULL COMMENT '사유(관리용)',
    PRIMARY KEY (`holiday_date`)
) COMMENT = '상담 휴무일';

-- ---------------------------------------------------------
-- 운영 문구 설정
-- ---------------------------------------------------------
-- 인사말·안내문은 종류가 계속 늘어나는데 그때마다 컬럼을 추가하면 마이그레이션이 계속
-- 붙는다. 값이 전부 짧은 텍스트 한 덩어리라 key-value로 둔다.
-- (`key`는 MySQL 예약어라 setting_key를 쓴다.)
CREATE TABLE `chat_setting`
(
    `setting_key`   VARCHAR(50)  NOT NULL COMMENT '설정 키',
    `setting_value` TEXT         NOT NULL COMMENT '설정 값',
    `description`   VARCHAR(200) NULL COMMENT '관리자 화면에 보여줄 설명',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (`setting_key`)
) COMMENT = '상담 챗봇 운영 문구';

-- ---------------------------------------------------------
-- 초기 데이터
-- ---------------------------------------------------------
-- 아래 문구는 전부 운영자가 관리자 화면에서 고칠 값이다. 여기 있는 건 "빈 화면으로
-- 뜨지 않게 하는" 초기값이지 확정된 카피가 아니다.
INSERT INTO `chat_setting` (`setting_key`, `setting_value`, `description`)
VALUES ('GREETING',
        '안녕하세요, 고객님. 펫토피아에 오신 것을 환영합니다.\n알맞은 문의 유형을 선택해주시면 더 빠르게 도와드릴 수 있어요.',
        '위젯을 열었을 때 처음 보이는 인사말'),
       ('AGENT_RECEIVED',
        '문의가 접수되었어요. 상담사가 확인한 뒤 이 창으로 답변드릴게요.',
        '상담사 연결을 선택했을 때 안내 문구'),
       ('OFFLINE_NOTICE',
        '지금은 상담 운영시간이 아니에요. 남겨주시면 운영시간에 순서대로 답변드릴게요.',
        '운영시간 외 안내 문구'),
       ('AI_LIMIT_NOTICE',
        '상담사가 답변을 준비하고 있어요. 답변이 도착하면 이어서 문의하실 수 있어요.',
        'AI 답변 한도를 모두 쓴 뒤 안내 문구');

INSERT INTO `chat_menu` (`code`, `label`, `display_order`, `answer_type`, `fixed_answer`)
VALUES ('VENDOR_INQUIRY', '1. 참가업체 문의', 1, 'FIXED',
        '참가업체 문의는 마이페이지 > 참가 신청에서 진행 상황을 확인하실 수 있어요.\n신청서 작성과 부스 배정 관련 문의는 이곳에서 상담사에게 연결해드릴 수 있습니다.'),
       ('FAIR_INQUIRY', '2. 페어업체 문의', 2, 'FIXED',
        '박람회 개설과 운영 관련 문의는 페어업체 전용 메뉴에서 처리하실 수 있어요.\n개설 신청 현황은 마이페이지 > 행사 관리에서 확인하실 수 있습니다.'),
       ('OPERATION_INQUIRY', '3. 운영관련 문의', 3, 'FIXED',
        '예약 확인·변경·취소는 마이페이지 > 예약 내역에서 하실 수 있어요.\n입장은 예약 내역의 QR 코드로 진행됩니다.'),
       ('DIRECTIONS_INQUIRY', '4. 길 찾기 관련 문의', 4, 'FIXED',
        '행사장 위치와 오시는 길은 각 박람회 상세 페이지 하단에서 확인하실 수 있어요.\n행사장 내부 부스 위치는 부스 지도에서 찾아보실 수 있습니다.'),
       ('AGENT_CONNECT', '0. 상담사 연결', 5, 'AGENT', NULL);

-- 평일 09:00~18:00. 운영자가 관리자 화면에서 조정한다.
INSERT INTO `chat_business_hour` (`day_of_week`, `start_time`, `end_time`)
VALUES (1, '09:00:00', '18:00:00'),
       (2, '09:00:00', '18:00:00'),
       (3, '09:00:00', '18:00:00'),
       (4, '09:00:00', '18:00:00'),
       (5, '09:00:00', '18:00:00');
