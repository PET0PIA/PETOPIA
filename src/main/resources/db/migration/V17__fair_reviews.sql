-- =========================================================
-- V17__fair_reviews.sql
-- 페어 리뷰(fair_reviews) 테이블 신설
--
-- 한 사용자가 같은 행사에 리뷰를 여러 개 남길 수 있다(방문마다 감상이 다를 수 있어서
-- fair_id+user_id UNIQUE 제약을 걸지 않는다).
--
-- 리뷰는 로그인만 하면 누구나 작성할 수 있다(예매·방문 여부로 막지 않는다). 다만
-- 작성 시점에 그 행사를 실제 예매/방문한 이력이 있었는지를 is_verified_visit에
-- 저장해, 화면에서 "방문 인증" 뱃지를 보여주는 용도로 쓴다. 이 값은 리뷰 작성 API가
-- 작성 시점에 한 번 판단해 저장하는 스냅샷이라, 이후 그 예약이 취소되더라도 소급
-- 변경하지 않는다.
-- =========================================================

CREATE TABLE `fair_reviews` (
    `review_id`          BIGINT     NOT NULL AUTO_INCREMENT,
    `fair_id`            BIGINT     NOT NULL COMMENT '리뷰 대상 fairs.fair_id',
    `user_id`            BIGINT     NOT NULL COMMENT '작성자 users.user_id',
    `rating`             TINYINT    NOT NULL COMMENT '평점(1~5). 범위 검증은 애플리케이션에서 한다',
    `content`            TEXT       NOT NULL COMMENT '리뷰 내용',
    `is_verified_visit`  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '작성 시점에 이 사용자의 예매·방문 이력이 있었는지(뱃지 표시용 스냅샷)',
    `created_at`         DATETIME   NOT NULL,
    `updated_at`         DATETIME   NOT NULL,
    CONSTRAINT `PK_FAIR_REVIEWS` PRIMARY KEY (`review_id`),
    KEY `idx_fair_reviews_fair` (`fair_id`),
    KEY `idx_fair_reviews_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
