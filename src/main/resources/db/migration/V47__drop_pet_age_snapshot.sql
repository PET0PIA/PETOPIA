-- =========================================================
-- V47__drop_pet_age_snapshot.sql
-- reservation_pets.pet_age_snapshot 컬럼 정리
--
-- 설계 배경 (docs/visit-stats-pet-allergy-plan.md §5):
-- pets.age는 V14에서 birth_date로 대체됐지만 reservation_pets.pet_age_snapshot은
-- 그 정리에서 빠진 채 남아 있었다. V44 이후 예약 도메인은 pet_birth_date_snapshot에만
-- 값을 채우고 pet_age_snapshot은 항상 NULL이다. 통계 도메인의 selectAvgPetAge가
-- pet_birth_date_snapshot 기준으로 옮겨졌으므로, 이제 참조하는 곳이 없어 컬럼을 지운다.
-- =========================================================

ALTER TABLE `reservation_pets`
    DROP COLUMN `pet_age_snapshot`;
