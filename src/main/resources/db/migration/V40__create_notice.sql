-- =========================================================
-- V40__create_notice.sql
-- 공지사항(소식·이벤트) 테이블과 첨부파일 테이블 생성.
--
-- 홈의 "PETOPIA 소식" 섹션·마퀴 띠와 헤더 메뉴의 /news 는 지금까지 프론트 mock으로만 그려져
-- 있었다. 최고 관리자가 실제로 공지를 등록·수정할 수 있도록 저장소를 만든다.
--
-- 설계 메모 (docs/design/notice-feature.md):
--  - category에 모집공고(RECRUIT)는 저장하지 않는다. 행사별 모집공고는 recruit_notice 테이블에
--    이미 있어서, 목록 조회 시 서버가 두 곳을 합쳐 내려준다(같은 내용을 두 번 쓰지 않게).
--  - content는 에디터가 만든 HTML이라 본문 안에 <img>가 섞인다. TEXT(64KB)로는 빠듯해 MEDIUMTEXT.
--  - fair_id는 "이 공지가 어느 행사에 대한 것인지"를 가리키는 선택값이다. 지금은 목록·상세의
--    행사 배지와 링크에 쓰고, 나중에 예약자 문자 발송을 붙일 때 발송 대상 기준으로 재사용한다.
-- =========================================================

CREATE TABLE `notice`
(
    `notice_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '공지 ID',
    `category`     VARCHAR(20)  NOT NULL COMMENT 'NOTICE(공지) / EVENT(이벤트) / GUIDE(안내)',
    `title`        VARCHAR(200) NOT NULL COMMENT '공지 제목',
    `content`      MEDIUMTEXT   NOT NULL COMMENT '본문 HTML (에디터 작성 - 화면에 뿌릴 때 정화 필요)',
    `fair_id`      BIGINT       NULL     COMMENT '연결된 행사 fairs.fair_id (선택, 없으면 전체 대상 공지)',
    `is_published` TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '게시 여부 (0이면 관리자에게만 보임)',
    `is_pinned`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '상단 고정 여부 (홈 마퀴 띠 재료로도 쓴다)',
    `view_count`   INT          NOT NULL DEFAULT 0 COMMENT '조회수',
    `created_by`   BIGINT       NOT NULL COMMENT '등록 관리자 users.user_id',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (`notice_id`),
    -- 공개 목록은 항상 "게시된 것만 / 고정 먼저 / 최신순"으로 읽는다.
    KEY `idx_notice_list` (`is_published`, `is_pinned`, `created_at` DESC),
    CONSTRAINT `fk_notice_fair` FOREIGN KEY (`fair_id`) REFERENCES `fairs` (`fair_id`),
    CONSTRAINT `fk_notice_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '공지사항(소식·이벤트)';

CREATE TABLE `notice_attachment`
(
    `attachment_id` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '첨부파일 ID',
    `notice_id`     BIGINT       NOT NULL COMMENT 'notice.notice_id',
    `file_url`      VARCHAR(500) NOT NULL COMMENT '확정된 공개 URL (S3 uploads 경로)',
    `original_name` VARCHAR(255) NOT NULL COMMENT '화면에 보여줄 원본 파일명',
    `file_size`     BIGINT       NOT NULL COMMENT '파일 크기(byte) - 화면에는 "2.4MB"처럼 표시',
    `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '첨부 순서',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    PRIMARY KEY (`attachment_id`),
    KEY `idx_notice_attachment_notice` (`notice_id`),
    -- 공지를 지우면 첨부 행도 함께 지운다(고아 행 방지). S3 실제 파일 정리는 별도 과제.
    CONSTRAINT `fk_notice_attachment_notice` FOREIGN KEY (`notice_id`) REFERENCES `notice` (`notice_id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '공지사항 첨부파일';
