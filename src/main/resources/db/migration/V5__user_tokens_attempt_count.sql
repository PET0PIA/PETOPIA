-- =========================================================
-- V5__user_tokens_attempt_count.sql
-- user_tokens.attempt_count 추가
--
-- 이메일 인증 코드(6자리) 무제한 대입 시도를 막기 위해, 토큰별 검증 실패
-- 횟수를 기록한다. 5회 초과 시 서비스 레이어에서 해당 토큰을 폐기 처리한다.
-- =========================================================

ALTER TABLE `user_tokens`
    ADD COLUMN `attempt_count` TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER `expires_at`;
