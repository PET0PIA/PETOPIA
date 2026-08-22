-- =========================================================
-- V46__chat_rework.sql
-- 고정형 답변을 상담 세션에서 떼어내고, 상담 세션을 "상담원 연결" 하나로 한정한다.
-- =========================================================
--
-- 지금까지는 고정형 버튼을 눌러도 chat_conversation이 생겼다. 답변 한 줄을 읽고 끝나는
-- 대화가 상담 이력에 계속 쌓이고, 상담사 대기열 지표는 그만큼 실제 업무량과 어긋났다.
-- 이제 고정형은 세션을 만들지 않는다. 답변은 위젯이 즉시 렌더하고, 클릭만 집계 테이블에 남는다.
--
-- AI도 성격이 바뀐다. 버튼 유형(answer_type='AI')이 아니라, 상담원 연결 세션이 운영시간
-- 밖일 때의 대체 응대다. 그래서 "대화당 3회" 같은 횟수 한도가 사라진다 - 사람이 답할 수 없는
-- 시간에 답을 아끼는 것은 아낄 이유가 없는 절약이었다.

-- ---------------------------------------------------------
-- 1) 고정형 버튼 클릭 집계
-- ---------------------------------------------------------
-- 세션이 안 생기면 "어떤 문의가 많은가"를 셀 근거도 함께 사라진다. 그 축을 여기서 되살린다.
-- 상담 데이터와 분리된 지표 전용 테이블이라, 봇 트래픽으로 오염되어도 상담에는 영향이 없다.
CREATE TABLE `chat_menu_click`
(
    `click_id`   BIGINT   NOT NULL AUTO_INCREMENT COMMENT '클릭 ID',
    `menu_id`    BIGINT   NOT NULL COMMENT '눌린 문의 유형',
    `user_id`    BIGINT   NULL COMMENT '로그인 사용자',
    `guest_key`  CHAR(36) NULL COMMENT '비로그인 식별자',
    `clicked_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '클릭 시각',
    PRIMARY KEY (`click_id`),
    CONSTRAINT `fk_chat_menu_click_menu` FOREIGN KEY (`menu_id`) REFERENCES `chat_menu` (`menu_id`),
    -- user_id에 FK를 걸지 않는다. 지표 적재는 상담 흐름을 막지 않아야 하고, 탈퇴로 사용자
    -- 행이 지워질 때 지난 클릭 집계까지 함께 끌려가면 과거 지표가 조용히 바뀐다.
    INDEX `idx_chat_menu_click_menu_time` (`menu_id`, `clicked_at`)
) COMMENT = '고정형 문의 유형 클릭 집계';

-- ---------------------------------------------------------
-- 2) AI 유형 버튼 정리
-- ---------------------------------------------------------
-- 시딩에는 AI 유형이 없다(FIXED 4개 + AGENT 1개). 운영에서 손으로 만든 행에 대한 방어다.
UPDATE `chat_menu` SET `is_active` = 0 WHERE `answer_type` = 'AI';

-- ai_context를 메뉴에서 떼어 전역 chat_setting으로 옮긴다. AI가 더 이상 특정 버튼에 붙지
-- 않으므로, 참고 지식이 메뉴에 남아 있으면 "어느 메뉴의 컨텍스트를 쓸 것인가"라는 답 없는
-- 질문이 코드에 남는다.
ALTER TABLE `chat_menu`
    MODIFY COLUMN `answer_type` VARCHAR(10) NOT NULL COMMENT 'FIXED | AGENT',
    DROP COLUMN `ai_context`;

-- ---------------------------------------------------------
-- 3) 상태값 교체
-- ---------------------------------------------------------
-- AI_ANSWERED -> AI_HANDLED. 이름을 바꾸는 이유는 의미가 반대로 뒤집혔기 때문이다.
-- 기존 값은 "AI가 답했으니 사람이 이어받아야 함 + 입력 잠금"이었고, 새 값은 "자동 응대로
-- 마무리됨 + 사람 답변 대기 아님"이다. 같은 이름을 재정의하면 마이그레이션 이후 코드를 읽는
-- 사람이 옛 주석과 새 동작 사이에서 판단을 잃는다.
--
-- 이 UPDATE로 이미 잠겨 있던 운영 대화의 잠금이 풀린다. 의도한 동작이다 - 잠금 해제
-- 방향이라 사용자가 손해 보는 전이가 아니다.
UPDATE `chat_conversation` SET `status` = 'AI_HANDLED' WHERE `status` = 'AI_ANSWERED';

ALTER TABLE `chat_conversation`
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'BOT'
        COMMENT 'BOT | WAITING_AGENT | AI_HANDLED | IN_PROGRESS | CLOSED (CLOSED만 입력 잠금)',
    -- 삭제하지 않는다. 한도 집행에서는 빠지지만 "AI가 응대한 대화 수" 지표가 이 값에 걸려 있다.
    MODIFY COLUMN `ai_answer_count` INT NOT NULL DEFAULT 0
        COMMENT 'AI가 답한 횟수. 한도가 아니라 지표용 카운터',
    -- 대화당 진행 중 AI 호출을 1건으로 묶는다. 횟수 한도가 아니라 중복 제거다 - 사용자가
    -- 답을 기다리다 같은 질문을 연달아 보내면 호출이 동시에 여러 건 돌고, 답변이 순서 없이
    -- 한 창에 쌓인다. 앞 답변이 도착하면(= 이 값이 NULL로 돌아가면) 다음 질문은 그대로 답을 받는다.
    --
    -- 시각을 넣는 이유는 스스로 풀리게 하기 위해서다. 프로세스가 죽어 반납이 안 된 행을
    -- 불린으로 두면 그 대화의 자동 응대가 영구히 막힌다. 애플리케이션이 "N분보다 오래된
    -- 선점은 없는 것으로 본다"고 판정한다.
    ADD COLUMN `ai_call_started_at` DATETIME NULL
        COMMENT '진행 중인 AI 호출 시작 시각. NULL이면 진행 중 아님. 한도가 아니라 중복 방지용';

-- ---------------------------------------------------------
-- 4) 운영 문구
-- ---------------------------------------------------------
-- AI_LIMIT_NOTICE는 "자동 답변을 다 썼고 이제 입력이 잠긴다"는 안내였다. 한도와 잠금이
-- 함께 사라졌으므로 붙을 자리가 없다.
--
-- AI_ESCALATE_NOTICE(V41)는 남긴다. AI가 답을 거부하고 상담사에게 넘기는 경로는 한도와
-- 무관하게 남고, 오히려 한도가 사라진 뒤에는 이 문장이 "밤에 답이 안 오는 이유"를 설명하는
-- 유일한 안내가 된다.
DELETE FROM `chat_setting` WHERE `setting_key` = 'AI_LIMIT_NOTICE';

INSERT INTO `chat_setting` (`setting_key`, `setting_value`, `description`)
VALUES ('AI_CONTEXT', '',
        'AI가 참고할 도메인 지식. 비어 있으면 대부분 상담사에게 넘긴다'),
       -- 프롬프트 지시가 아니라 서버 후처리로 붙인다. 프롬프트로 부탁하면 붙는 날도 있고
       -- 안 붙는 날도 있다. 운영자가 문구를 고칠 수 있어야 한다는 요구까지 겹치면
       -- 설정값 + 서버 append가 유일하게 확정적인 방법이다.
       ('AI_CLOSING_NOTE', '더 상세한 답변이 필요하시면 운영시간에 다시 문의해주세요.',
        'AI 답변 끝에 서버가 덧붙이는 안내');

-- 첫 화면이 "인사말 + 버튼"만 남으므로 인사말이 곧 안내문이 된다.
UPDATE `chat_setting`
   SET `setting_value` = '안녕하세요, 펫토피아입니다.\n원하시는 문의 유형을 눌러주세요.'
 WHERE `setting_key` = 'GREETING';
