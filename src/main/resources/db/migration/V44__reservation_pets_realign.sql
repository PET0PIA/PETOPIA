-- =========================================================
-- V44__reservation_pets_realign.sql
-- 예약 시점 반려동물 스냅샷 정리 + 알레르기 스냅샷 추가
--
-- 설계 배경 (docs/pet-companion-reservation-plan.md §3-3):
-- reservation_pets는 V1에 만들어졌지만 한 번도 채워진 적이 없다(INSERT 코드 0건).
-- "반려동물 선택·변경은 예약 흐름에 포함하지 않는다"가 확정 정책이었기 때문이고,
-- 이번 작업이 팀 합의로 그 정책을 뒤집는다. 데이터가 없으므로 컬럼을 바꿔도 유실이 없다.
--
-- 다만 통계 도메인이 이 테이블을 직접 조회하고 있어 **DROP 없이 추가만** 한다.
-- 그래야 두 팀이 동시에 배포할 필요가 없다.
--
-- pet_age_snapshot은 컬럼만 남기고 값을 넣지 않는다(계속 NULL).
-- pets.age는 V14에서 삭제되고 birth_date로 대체됐다 - "age는 매년 값이 바뀌는 파생
-- 정보라, 불변 값인 생년월일을 저장하고 필요할 때 계산해서 쓴다". pet_age_snapshot은
-- 그 정리에서 빠진 잔재다. 생년월일을 저장하면 "입장 시점의 나이"를 정확히 계산할 수
-- 있어 과거 행사 통계가 시간이 지나도 변하지 않는다.
-- 이 계약은 docs/visit-stats-pet-allergy-plan.md에 통계 도메인에 전달해 두었다.
-- =========================================================

ALTER TABLE `reservation_pets`
    ADD COLUMN `pet_birth_date_snapshot`  DATE       NULL
        COMMENT '예약 시점 생년월일. 입장 시점 나이를 정확히 계산하기 위해 나이 대신 저장한다' AFTER `pet_breed_snapshot`,
    ADD COLUMN `pet_has_allergy_snapshot` BOOLEAN    NULL
        COMMENT '예약 시점 알레르기 여부. NULL=미입력 / 0=없음 / 1=있음' AFTER `pet_age_snapshot`,
    MODIFY COLUMN `pet_age_snapshot` INT NULL
        COMMENT '사용하지 않음(항상 NULL). pet_birth_date_snapshot으로 대체됐고, 통계 도메인이 조회 중이라 컬럼만 남긴다',
    MODIFY COLUMN `pet_allergy_snapshot` VARCHAR(255) NULL
        COMMENT '예약 시점 알레르기 라벨 목록(콤마 구분, 예: 닭고기,꽃가루). 목록·상세 표시용';


-- =========================================================
-- 예약 스냅샷의 알레르기 - 정규화 경로
--
-- 플랫 텍스트(pet_allergy_snapshot)와 중복 저장이다. 플랫 텍스트는 목록·상세에 그대로
-- 뿌리기 편하고, 이 테이블은 GROUP BY로 분포를 뽑을 때 인덱스를 탄다(FIND_IN_SET은 못 탄다).
-- V39 booth_feedbacks가 fair_id·business_id를 "통계 조회 편의를 위해 비정규화해서 같이
-- 저장한다"고 명시한 것과 같은 방식이다.
--
-- 마스터가 나중에 is_active=0으로 비활성화돼도 이 이력은 남는다 - 통계에서 과거 데이터를
-- 볼 때는 is_active로 필터하지 않는다.
-- =========================================================
CREATE TABLE `reservation_pet_allergies` (
    `reservation_pet_id`  BIGINT       NOT NULL COMMENT 'reservation_pets.reservation_pet_id',
    `allergy_type_id`     BIGINT       NOT NULL COMMENT 'pet_allergy_types.allergy_type_id',
    `other_text_snapshot` VARCHAR(100) NULL     COMMENT 'requires_text=1인 항목(기타)에 사용자가 적었던 내용',
    CONSTRAINT `PK_RESERVATION_PET_ALLERGIES` PRIMARY KEY (`reservation_pet_id`, `allergy_type_id`),
    KEY `idx_reservation_pet_allergies_type` (`allergy_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
