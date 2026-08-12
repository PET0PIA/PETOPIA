-- =========================================================
-- V18__add_booth_favorites.sql
-- 부스 즐겨찾기 기능 - 사용자별로 부스를 즐겨찾기에 담아두는 테이블 신설
--
-- UNIQUE(user_id, booth_id)로 같은 부스를 중복 즐겨찾기하지 못하게 DB가
-- 막는다 - 서비스 계층에서 INSERT IGNORE로 멱등 처리하므로(중복 클릭해도
-- 에러 없이 조용히 무시), 이 제약이 그 멱등성의 실제 근거가 된다.
-- =========================================================

CREATE TABLE `booth_favorites` (
    `booth_favorite_id` BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT   NOT NULL COMMENT 'users.user_id',
    `booth_id`          BIGINT   NOT NULL COMMENT 'booth.booth_id',
    `created_at`        DATETIME NOT NULL,
    CONSTRAINT `PK_BOOTH_FAVORITES` PRIMARY KEY (`booth_favorite_id`),
    CONSTRAINT `UK_BOOTH_FAVORITE_USER_BOOTH` UNIQUE (`user_id`, `booth_id`),
    KEY `idx_booth_favorites_booth` (`booth_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;