-- =========================================================
-- V19__banner_hero_fields.sql
-- 메인페이지 히어로 배너 연출(부제/CTA 버튼/배경색)을 위한 필드 추가
-- 주 링크의 이동 URL은 기존 link_url을 재사용하고, 버튼 문구(link_label)만 추가한다.
-- 보조 링크(link2_label/link2_url)는 둘 다 NULL이면 버튼 자체가 없는 배너로 취급한다.
-- =========================================================

ALTER TABLE `banner`
    ADD COLUMN `eyebrow`     VARCHAR(50)   NULL COMMENT '상단 강조 라벨 (예: 예매 오픈)' AFTER `title`,
    ADD COLUMN `subtitle`    VARCHAR(300)  NULL COMMENT '부제 문구' AFTER `eyebrow`,
    ADD COLUMN `link_label`  VARCHAR(50)   NULL COMMENT '주 링크 버튼 문구 (이동 URL은 link_url 재사용)' AFTER `link_target`,
    ADD COLUMN `link2_label`  VARCHAR(50)   NULL COMMENT '보조 링크 버튼 문구 (NULL이면 보조 버튼 없음)' AFTER `link_label`,
    ADD COLUMN `link2_url`    VARCHAR(2000) NULL COMMENT '보조 링크 버튼 이동 URL (NULL이면 보조 버튼 없음)' AFTER `link2_label`,
    ADD COLUMN `link2_target` VARCHAR(10)   NULL COMMENT '보조 링크 이동 방식: SELF | BLANK (link2_url 없으면 무의미)' AFTER `link2_url`,
    ADD COLUMN `bg_color`     VARCHAR(10)   NULL COMMENT '슬라이드 배경색 (hex, 예: #FBD9BD)' AFTER `link2_target`;
