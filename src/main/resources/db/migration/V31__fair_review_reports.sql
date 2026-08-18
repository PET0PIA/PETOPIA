-- =========================================================
-- V31__fair_review_reports.sql
-- 페어 리뷰 신고(fair_review_reports) 테이블 신설
--
-- 신고 사유는 고정된 4가지(SPAM/ABUSE/FALSE_INFO/ETC) 중 하나만 저장한다. ETC를 고른
-- 경우에만 reason_detail에 상세 사유를 남길 수 있다(그 외에는 NULL).
--
-- 한 사용자가 같은 리뷰를 여러 번 신고하지 못하게 (review_id, reporter_user_id) UNIQUE
-- 제약을 건다 - booth_favorites가 (user_id, booth_id) UNIQUE로 중복 즐겨찾기를 막는 것과
-- 같은 패턴이다.
--
-- 신고 처리 상태(검토 대기/처리 완료 등)는 이 마이그레이션에 포함하지 않았다 - 지금은
-- 신고를 접수·기록하는 것까지만 범위이고, 관리자가 신고를 검토·처리하는 화면은 아직
-- 범위 밖이다(petopia-review-feature-plan 스킬 참고). 필요해지면 별도 마이그레이션으로
-- status 컬럼을 추가한다.
-- =========================================================

CREATE TABLE `fair_review_reports` (
    `review_report_id`  BIGINT       NOT NULL AUTO_INCREMENT,
    `review_id`          BIGINT       NOT NULL COMMENT '신고 대상 fair_reviews.review_id',
    `reporter_user_id`   BIGINT       NOT NULL COMMENT '신고자 users.user_id',
    `reason`             VARCHAR(20)  NOT NULL COMMENT 'SPAM / ABUSE / FALSE_INFO / ETC',
    `reason_detail`      VARCHAR(500) NULL     COMMENT 'reason=ETC일 때만 채워지는 상세 사유',
    `created_at`         DATETIME     NOT NULL,
    CONSTRAINT `PK_FAIR_REVIEW_REPORTS` PRIMARY KEY (`review_report_id`),
    CONSTRAINT `UK_FAIR_REVIEW_REPORT_REVIEW_REPORTER` UNIQUE (`review_id`, `reporter_user_id`),
    CONSTRAINT `CK_FAIR_REVIEW_REPORTS_REASON` CHECK (`reason` IN ('SPAM', 'ABUSE', 'FALSE_INFO', 'ETC')),
    CONSTRAINT `CK_FAIR_REVIEW_REPORTS_REASON_DETAIL` CHECK (
        (`reason` = 'ETC' AND `reason_detail` IS NOT NULL) OR
        (`reason` <> 'ETC' AND `reason_detail` IS NULL)
    ),
    KEY `idx_fair_review_reports_review` (`review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
