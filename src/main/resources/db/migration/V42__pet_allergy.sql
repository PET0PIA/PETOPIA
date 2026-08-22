-- =========================================================
-- V42__pet_allergy.sql
-- 반려동물 알레르기 등록 - 알레르기 유형 마스터 + 반려동물별 선택 + 알레르기 여부
--
-- 설계 배경 (docs/pet-companion-reservation-plan.md §3-1):
-- 알레르기 항목은 앞으로 늘어날 수 있어 CHECK 제약이나 하드코딩이 아니라 마스터
-- 테이블로 둔다. 항목 추가가 INSERT 한 줄이 되고 배포가 필요 없다. 이 저장소는 이미
-- 같은 기준으로 갈라져 있다 - companion_type(설문 설계값, 사실상 고정)은 CHECK,
-- feedback_tags(늘어나는 목록)는 마스터 테이블 + is_active. 알레르기는 후자 쪽이다.
--
-- 순수 추가만 한다. 기존 테이블에서 지우거나 바꾸는 컬럼이 없어 다른 도메인
-- (AI 부스 추천·통계)과 배포 순서를 맞출 필요가 없다.
-- =========================================================


-- =========================================================
-- (1) 알레르기 유형 마스터
--
-- is_active는 soft delete다. 이미 쌓인 선택 이력(pet_allergies·예약 스냅샷)이 가리키는
-- allergy_type_id가 항상 유효하게 남도록 물리 삭제를 하지 않는다(feedback_tags와 같은 이유).
-- category는 화면의 묶음(사료·간식 / 환경 / 기타) 3개로 고정된 구조값이라 CHECK로 둔다.
-- 늘어나는 쪽은 항목(code)이고 묶음은 아니다.
--
-- requires_text는 "기타는 직접 입력칸이 있다"는 규칙을 데이터로 내려주는 값이다.
-- 프론트가 code='OTHER'를 특수 분기로 알고 있지 않아도 되게 한다.
-- =========================================================
CREATE TABLE `pet_allergy_types` (
    `allergy_type_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `code`            VARCHAR(30)  NOT NULL COMMENT '코드값. CHICKEN, POLLEN, OTHER 등',
    `category`        VARCHAR(20)  NOT NULL COMMENT 'FOOD(음식) / ENVIRONMENT(환경) / OTHER(기타)',
    `label`           VARCHAR(50)  NOT NULL COMMENT '화면에 노출되는 항목명. 예: 닭고기',
    `requires_text`   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1이면 직접 입력칸이 필요한 항목(기타)',
    `sort_order`      INT          NOT NULL DEFAULT 0 COMMENT '같은 category 내 노출 순서',
    `is_active`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '0이면 신규 선택 불가(soft delete). 과거 선택 이력 보존용으로 물리 삭제하지 않음',
    `created_at`      DATETIME     NOT NULL,
    `updated_at`      DATETIME     NOT NULL,
    CONSTRAINT `PK_PET_ALLERGY_TYPES` PRIMARY KEY (`allergy_type_id`),
    CONSTRAINT `UK_PET_ALLERGY_TYPES_CODE` UNIQUE (`code`),
    CONSTRAINT `CK_PET_ALLERGY_TYPES_CATEGORY` CHECK (`category` IN ('FOOD', 'ENVIRONMENT', 'OTHER')),
    KEY `idx_pet_allergy_types_active_sort` (`is_active`, `category`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- (2) 반려동물별 알레르기 선택 (다중)
--
-- PK가 (pet_id, allergy_type_id)라 같은 항목을 두 번 고르는 것이 DB 차원에서 막힌다.
-- other_text는 requires_text=1인 항목(기타)일 때만 채운다.
--
-- FK를 두지 않는 것은 이 저장소의 다수 스타일을 따른 것이다(feedback_tags 계열,
-- V1 대부분의 테이블이 FK 없이 COMMENT로 참조 대상만 적어둔다). 반려동물 삭제 시의
-- 정리는 PetService.deletePet이 이 테이블을 먼저 지우는 방식으로 명시적으로 처리한다.
-- =========================================================
CREATE TABLE `pet_allergies` (
    `pet_id`          BIGINT       NOT NULL COMMENT 'pets.pet_id',
    `allergy_type_id` BIGINT       NOT NULL COMMENT 'pet_allergy_types.allergy_type_id',
    `other_text`      VARCHAR(100) NULL COMMENT 'requires_text=1인 항목(기타)에 사용자가 직접 적은 내용',
    CONSTRAINT `PK_PET_ALLERGIES` PRIMARY KEY (`pet_id`, `allergy_type_id`),
    KEY `idx_pet_allergies_type` (`allergy_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- (3) 알레르기 여부 - 3값 (is_neutered와 같은 패턴)
--
-- NULL = 아직 안 물어봄 / FALSE = 없음 / TRUE = 있음.
-- pet_allergies가 비어 있을 때 "없다고 답했다"와 "물어본 적 없다"를 구분하기 위해
-- 목록과 별도로 둔다. 통계에서 분모가 달라지는 지점이라 이 구분이 필요하다.
-- =========================================================
ALTER TABLE `pets`
    ADD COLUMN `has_allergy` BOOLEAN NULL COMMENT '알레르기 여부. NULL=미입력 / 0=없음 / 1=있음' AFTER `is_neutered`;


-- =========================================================
-- (4) 시드 20행
-- 음식 11 + 환경 8 + 기타 1. 기타만 requires_text=1이다.
-- =========================================================
INSERT INTO `pet_allergy_types` (`code`, `category`, `label`, `requires_text`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES
-- 음식(사료·간식) 11
('CHICKEN',    'FOOD',        '닭고기',         0,  1, 1, NOW(), NOW()),
('BEEF',       'FOOD',        '소고기',         0,  2, 1, NOW(), NOW()),
('PORK',       'FOOD',        '돼지고기',       0,  3, 1, NOW(), NOW()),
('LAMB',       'FOOD',        '양고기',         0,  4, 1, NOW(), NOW()),
('FISH',       'FOOD',        '생선',           0,  5, 1, NOW(), NOW()),
('EGG',        'FOOD',        '달걀',           0,  6, 1, NOW(), NOW()),
('DAIRY',      'FOOD',        '유제품',         0,  7, 1, NOW(), NOW()),
('WHEAT',      'FOOD',        '밀',             0,  8, 1, NOW(), NOW()),
('CORN',       'FOOD',        '옥수수',         0,  9, 1, NOW(), NOW()),
('SOY',        'FOOD',        '콩',             0, 10, 1, NOW(), NOW()),
('RICE',       'FOOD',        '쌀',             0, 11, 1, NOW(), NOW()),
-- 환경 8
('DUST_MITE',  'ENVIRONMENT', '집먼지진드기',   0,  1, 1, NOW(), NOW()),
('HOUSE_DUST', 'ENVIRONMENT', '집먼지',         0,  2, 1, NOW(), NOW()),
('POLLEN',     'ENVIRONMENT', '꽃가루',         0,  3, 1, NOW(), NOW()),
('GRASS',      'ENVIRONMENT', '잔디·풀',        0,  4, 1, NOW(), NOW()),
('TREE',       'ENVIRONMENT', '나무·수목',      0,  5, 1, NOW(), NOW()),
('MOLD',       'ENVIRONMENT', '곰팡이',         0,  6, 1, NOW(), NOW()),
('FLEA',       'ENVIRONMENT', '벼룩',           0,  7, 1, NOW(), NOW()),
('INSECT',     'ENVIRONMENT', '벌레·곤충',      0,  8, 1, NOW(), NOW()),
-- 기타 1 - 목록에 없는 항목을 직접 적는다
('OTHER',      'OTHER',       '기타',           1,  1, 1, NOW(), NOW());
