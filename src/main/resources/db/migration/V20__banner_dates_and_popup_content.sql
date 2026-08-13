-- =========================================================
-- V20__banner_dates_and_popup_content.sql
-- 1) 배너 노출 시작/종료 일시를 필수값으로 변경한다.
--    더 이상 NULL(즉시 시작/무기한 노출)을 지원하지 않으며,
--    관리자가 항상 캘린더로 시작/종료일을 지정해야 한다.
-- 2) 팝업이 이미지 없이 텍스트만으로도 구성될 수 있도록
--    본문(subtitle), 버튼 문구(link_label), 배경색(bg_color)을 추가하고
--    image_key를 선택값으로 변경한다.
--    버튼의 이동 URL은 기존 link_url을 재사용하고 문구(link_label)만 추가한다.
-- =========================================================

UPDATE `banner` SET `started_at` = COALESCE(`started_at`, `created_at`) WHERE `started_at` IS NULL;
UPDATE `banner` SET `ended_at` = COALESCE(`ended_at`, DATE_ADD(`created_at`, INTERVAL 30 DAY)) WHERE `ended_at` IS NULL;

ALTER TABLE `banner`
    MODIFY COLUMN `started_at` DATETIME NOT NULL COMMENT '노출 시작일시',
    MODIFY COLUMN `ended_at`   DATETIME NOT NULL COMMENT '노출 종료일시',
    MODIFY COLUMN `title`      VARCHAR(100) NOT NULL COMMENT '배너 제목 (화면에 큰 헤드라인으로 노출)';

ALTER TABLE `popup`
    MODIFY COLUMN `image_key` VARCHAR(500) NULL COMMENT 'S3 object key (텍스트 전용 팝업은 NULL)',
    ADD COLUMN `subtitle`   VARCHAR(1000) NULL COMMENT '본문 문구 (이미지 없는 텍스트 팝업에 사용)' AFTER `title`,
    ADD COLUMN `link_label` VARCHAR(50)   NULL COMMENT '버튼 문구 (NULL이면 버튼 없음, 이동 URL은 link_url 재사용)' AFTER `link_target`,
    ADD COLUMN `bg_color`   VARCHAR(10)   NULL COMMENT '팝업 배경색 (hex, 예: #FBD9BD)' AFTER `link_label`;
