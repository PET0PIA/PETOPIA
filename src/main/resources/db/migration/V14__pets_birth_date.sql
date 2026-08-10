-- =========================================================
-- V14__pets_birth_date.sql
-- pets.age -> pets.birth_date 로 대체
--
-- age는 매년 값이 바뀌는 파생 정보라, 불변 값인 생년월일을 저장하고
-- 필요할 때 계산해서 쓰는 방식으로 변경한다 (V2__users_birth_date.sql과 동일한 이유).
-- 생년월일을 모르는 채로 등록하는 경우도 있어 NULL 허용은 유지한다.
-- =========================================================

ALTER TABLE `pets`
    DROP COLUMN `age`,
    ADD COLUMN `birth_date` DATE NULL AFTER `breed`;
