-- =========================================================
-- V2__users_birth_date.sql
-- users.age -> users.birth_date 로 대체
--
-- age는 매년 값이 바뀌는 파생 정보라, 불변 값인 생년월일을 저장하고
-- 필요할 때 계산해서 쓰는 방식으로 변경한다.
-- =========================================================

ALTER TABLE `users`
    DROP COLUMN `age`,
    ADD COLUMN `birth_date` DATE NOT NULL AFTER `nickname`;
