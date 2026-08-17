-- =========================================================
-- V35__fair_reviews_version.sql
-- fair_reviews.version 추가
--
-- 리뷰 수정(PATCH /api/fairs/{fairId}/reviews/{reviewId})은 별도 잠금 없이 rating/content를
-- 그대로 덮어쓴다. 같은 사용자가 두 탭·두 기기에서 동시에 같은 리뷰를 수정하면 나중 저장이
-- 앞선 저장 내용을 조용히 덮어쓸 수 있다.
--
-- halls.booth_layout_version(V8)과 같은 패턴으로 낙관적 락 버전을 둔다. 클라이언트는 조회
-- 시점에 받은 version을 수정 요청에 그대로 실어 보내야 하고, 서비스는
--   UPDATE fair_reviews SET ..., version = version + 1 WHERE review_id = ? AND version = ?
-- 형태의 조건부 UPDATE로 버전을 원자적으로 검증·증가시킨다. 영향받은 행이 없으면(= 그 사이
-- 다른 수정이 있었으면) 409(REVIEW_VERSION_CONFLICT)로 거부한다.
-- =========================================================

ALTER TABLE `fair_reviews`
    ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전' AFTER `updated_at`;
