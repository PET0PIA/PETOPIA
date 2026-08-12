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
    `rating`             TINYINT    NOT NULL COMMENT '평점(1~5)',
    `content`            TEXT       NOT NULL COMMENT '리뷰 내용',
    `is_verified_visit`  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '작성 시점에 이 사용자의 예매·방문 이력이 있었는지(뱃지 표시용 스냅샷)',
    `created_at`         DATETIME   NOT NULL,
    `updated_at`         DATETIME   NOT NULL,
    CONSTRAINT `PK_FAIR_REVIEWS` PRIMARY KEY (`review_id`),
    CONSTRAINT `CK_FAIR_REVIEWS_RATING` CHECK (`rating` BETWEEN 1 AND 5),
    -- 목록 조회가 fair_id/user_id로 필터링 후 created_at DESC로 정렬해 LIMIT/OFFSET 페이징하므로
    -- (FairReviewMapper#selectByFairId/#selectByUserId 참고), 단일 컬럼 인덱스 대신 정렬까지
    -- 커버하는 복합 인덱스를 둔다. review_id를 마지막에 둔 건 같은 created_at 값이 여러 건일 때
    -- 페이지 경계에서 순서가 흔들리지 않게 하기 위해서다.
    KEY `idx_fair_reviews_fair_created` (`fair_id`, `created_at`, `review_id`),
    KEY `idx_fair_reviews_user_created` (`user_id`, `created_at`, `review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
