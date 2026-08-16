-- =========================================================
-- V32__fair_review_replies.sql
-- 행사 담당자 리뷰 답글(fair_review_replies) 테이블 신설
--
-- 리뷰 하나당 답글은 1개만 허용한다(review_id UNIQUE) - 대부분의 커머스 리뷰 답글
-- 기능과 같은 방식. 이미 답글이 있으면 새로 달지 못하고 기존 답글을 수정(PATCH)해야 한다.
--
-- fair_id는 review_id로도 조인해서 알 수 있지만, "이 행사 담당자가 단 답글 목록" 같은
-- 조회 편의를 위해 비정규화해서 같이 저장한다 - booth_visits가 fair_id/business_id를
-- 비정규화하는 것과 같은 이유.
-- =========================================================

CREATE TABLE `fair_review_replies` (
    `review_reply_id` BIGINT   NOT NULL AUTO_INCREMENT,
    `review_id`        BIGINT   NOT NULL COMMENT '답글 대상 fair_reviews.review_id. 리뷰당 답글 1개',
    `fair_id`          BIGINT   NOT NULL COMMENT 'fairs.fair_id. 조회 편의용 비정규화',
    `admin_user_id`    BIGINT   NOT NULL COMMENT '작성한 행사 담당자 users.user_id',
    `content`          TEXT     NOT NULL,
    `created_at`       DATETIME NOT NULL,
    `updated_at`       DATETIME NOT NULL,
    CONSTRAINT `PK_FAIR_REVIEW_REPLIES` PRIMARY KEY (`review_reply_id`),
    CONSTRAINT `UK_FAIR_REVIEW_REPLY_REVIEW` UNIQUE (`review_id`),
    KEY `idx_fair_review_replies_fair` (`fair_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
