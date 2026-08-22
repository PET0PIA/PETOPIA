-- =========================================================
-- V43__fairs_pet_policy.sql
-- 행사의 반려동물 동반 가능 여부
--
-- 설계 배경 (docs/pet-companion-reservation-plan.md §2-3 P1, §3-2):
-- 지금까지 "이 행사에 반려동물을 데려가도 되는가"를 코드가 판단할 방법이 없었다.
-- fairs.category(DOG/CAT/ETC)는 행사의 *주제*일 뿐이라 "강아지 박람회지만 동반은 금지"를
-- 구분하지 못하고, 유의사항(notice_text) 본문에 글로 적는 것이 유일한 수단이었다.
--
-- DEFAULT TRUE인 이유(정책 P1): 반려동물 박람회 플랫폼이고, 기존 행사 데이터를 일괄로
-- 채워야 하는데 FALSE로 채우면 이미 공개된 행사들이 갑자기 동반 불가로 바뀐다.
--
-- NOT NULL인 이유: "미입력"이라는 제3의 상태를 두면 예약 화면이 동반 UI를 띄울지
-- 말지 판단할 수 없다. is_neutered/has_allergy와 달리 이 값은 행사 운영 규칙이라
-- 반드시 둘 중 하나로 정해져 있어야 한다.
-- =========================================================

ALTER TABLE `fairs`
    ADD COLUMN `pet_allowed` BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT '반려동물 동반 가능 여부. 기본값 TRUE(정책 P1)' AFTER `notice_text`;
