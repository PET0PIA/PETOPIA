-- =========================================================
-- V8__add_booth_layout_version_to_halls.sql
-- halls.booth_layout_version 추가
--
-- 부스 슬롯 일괄저장(PUT /api/fairs/{fairId}/halls/{hallId}/booth-slots)은
-- 현재 레이아웃을 통째로 읽어 diff 후 반영하는 방식이라, 두 관리자(또는 같은
-- 관리자의 두 탭)가 같은 홀을 동시에 편집하면 나중 저장이 앞선 저장 내용을
-- 조용히 덮어쓰거나 삭제할 수 있다.
--
-- 홀 단위 버전 카운터를 두고, 저장 요청에 클라이언트가 조회 시점에 받은
-- expectedVersion을 함께 보내게 한다. 서비스는
--   UPDATE halls SET booth_layout_version = booth_layout_version + 1
--    WHERE hall_id = ? AND booth_layout_version = ?
-- 형태의 조건부 UPDATE로 버전을 원자적으로 검증·증가시키고, 영향받은 행이
-- 없으면(= 그 사이 다른 저장이 있었으면) 409로 거부한다.
-- =========================================================

ALTER TABLE `halls`
    ADD COLUMN `booth_layout_version` BIGINT NOT NULL DEFAULT 0 COMMENT '부스 배치 낙관적 락 버전' AFTER `floor_plan_image_url`;
