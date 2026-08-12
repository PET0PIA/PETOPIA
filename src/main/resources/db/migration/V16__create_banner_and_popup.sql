-- =========================================================
-- V16__create_banner_and_popup.sql
-- 메인페이지 광고 배너 및 팝업 테이블 생성
-- =========================================================

CREATE TABLE `banner`
(
    `banner_id`   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '배너 ID',
    `title`       VARCHAR(100)  NOT NULL COMMENT '관리용 제목 (사용자 미노출)',
    `image_key`   VARCHAR(500)  NOT NULL COMMENT 'S3 object key',
    `link_url`    VARCHAR(2000) NULL     COMMENT '클릭 시 이동 URL',
    `link_target` VARCHAR(10)   NOT NULL DEFAULT 'SELF' COMMENT 'SELF | BLANK',
    `sort_order`  INT           NOT NULL DEFAULT 0 COMMENT '노출 순서 (오름차순)',
    `is_active`   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '노출 여부',
    `started_at`  DATETIME      NULL     COMMENT '노출 시작일시 (NULL = 즉시)',
    `ended_at`    DATETIME      NULL     COMMENT '노출 종료일시 (NULL = 무기한)',
    `created_by`  BIGINT        NOT NULL COMMENT '등록 관리자 user_id',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (`banner_id`),
    CONSTRAINT `fk_banner_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`)
) COMMENT = '메인페이지 광고 배너';

CREATE TABLE `popup`
(
    `popup_id`    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '팝업 ID',
    `title`       VARCHAR(100)  NOT NULL COMMENT '관리용 제목 (사용자 미노출)',
    `image_key`   VARCHAR(500)  NOT NULL COMMENT 'S3 object key',
    `link_url`    VARCHAR(2000) NULL     COMMENT '클릭 시 이동 URL',
    `link_target` VARCHAR(10)   NOT NULL DEFAULT 'SELF' COMMENT 'SELF | BLANK',
    `width`       INT           NULL     CHECK (`width` > 0) COMMENT '팝업 너비(px)',
    `height`      INT           NULL     CHECK (`height` > 0) COMMENT '팝업 높이(px)',
    `is_active`   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '노출 여부',
    `started_at`  DATETIME      NULL     COMMENT '노출 시작일시 (NULL = 즉시)',
    `ended_at`    DATETIME      NULL     COMMENT '노출 종료일시 (NULL = 무기한)',
    `created_by`  BIGINT        NOT NULL COMMENT '등록 관리자 user_id',
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일시',
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (`popup_id`),
    CONSTRAINT `fk_popup_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`)
) COMMENT = '메인페이지 광고 팝업';
