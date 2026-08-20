-- =========================================================
-- V39__unified_review_feedback.sql
-- 리뷰 도메인 전면 재설계 - 별점+자유서술 리뷰 폐기, 태그 기반 통합 리뷰로 교체
--
-- 폐기 배경: 박람회는 회차마다 구성이 달라질 수 있어 "자유서술 텍스트가 다음 회차
-- 운영에 그대로 참고되기 어렵다"는 판단으로, 기존 fair_reviews(별점+텍스트)와 그에
-- 딸린 신고(fair_review_reports)·답글(fair_review_replies) 기능을 모두 폐기하고,
-- 체크박스형 태그 선택 기반의 새 리뷰 플로우로 교체한다. 운영 중인 서비스가 아니라
-- 기존 데이터 보존 없이 DROP 후 재설계한다.
--
-- 새 구조 요약:
--   1) feedback_tags        : 태그 마스터(전역, soft delete만 허용, 카테고리/성격/scope로 분류)
--   2) fair_reviews          : 리뷰 1건 = 방문 1회 단위. 행사 전체에 대한 응답(동반유형/
--                              방문목적/재방문의향)만 담고, 태그 선택은 fair_review_tag_selections로 분리
--   3) fair_review_tag_selections : 리뷰가 고른 행사 전체용(scope=FAIR) 태그들
--   4) booth_feedbacks       : 리뷰 하나에 딸린 부스별 평가(부스당 1건). 리뷰 마법사에서
--                              최대 3개 부스까지 선택해 이어서 작성
--   5) booth_feedback_selections : 부스 평가가 고른 부스용(scope=BOOTH) 태그들
--
-- 태그 마스터는 잠그지 않는다(행사가 리뷰를 받기 시작한 뒤에도 카테고리/태그 추가·수정·
-- 삭제 가능). 대신 삭제는 물리 삭제가 아니라 is_active=0 처리만 하는 soft delete로 제한해,
-- 이미 쌓인 선택 이력(fair_review_tag_selections/booth_feedback_selections)이 가리키는
-- tag_id가 항상 유효하게 남도록 한다. 집계는 매번 tag_id 기준으로 다시 계산되므로 라벨을
-- 수정해도 과거 통계에 그 수정 내용이 그대로 반영된다(라벨 문자열을 사실 테이블에 중복
-- 저장하지 않기 때문).
-- =========================================================

DROP TABLE `fair_review_replies`;
DROP TABLE `fair_review_reports`;
DROP TABLE `fair_reviews`;


-- =========================================================
-- (1) 태그 마스터
-- =========================================================
CREATE TABLE `feedback_tags` (
    `tag_id`      BIGINT      NOT NULL AUTO_INCREMENT,
    `scope`       VARCHAR(10) NOT NULL COMMENT 'FAIR(행사 전체용) / BOOTH(부스별용)',
    `category`    VARCHAR(30) NOT NULL COMMENT '태그 그룹 코드. scope별로 유효한 값이 다름(아래 CHECK 참고)',
    `sentiment`   VARCHAR(10) NOT NULL COMMENT 'POSITIVE(좋았어요) / NEGATIVE(아쉬웠어요)',
    `label`       VARCHAR(50) NOT NULL COMMENT '화면에 노출되는 태그 문구',
    `sort_order`  INT         NOT NULL DEFAULT 0 COMMENT '같은 scope+category 내 노출 순서',
    `is_active`   TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '0이면 신규 선택 불가(soft delete). 과거 선택 이력 보존용으로 물리 삭제하지 않음',
    `created_at`  DATETIME    NOT NULL,
    `updated_at`  DATETIME    NOT NULL,
    CONSTRAINT `PK_FEEDBACK_TAGS` PRIMARY KEY (`tag_id`),
    CONSTRAINT `UK_FEEDBACK_TAGS_SCOPE_LABEL` UNIQUE (`scope`, `label`),
    CONSTRAINT `CK_FEEDBACK_TAGS_SCOPE` CHECK (`scope` IN ('FAIR', 'BOOTH')),
    CONSTRAINT `CK_FEEDBACK_TAGS_SENTIMENT` CHECK (`sentiment` IN ('POSITIVE', 'NEGATIVE')),
    CONSTRAINT `CK_FEEDBACK_TAGS_CATEGORY` CHECK (
        (`scope` = 'FAIR'  AND `category` IN ('GUIDE_OPERATION', 'SAFETY_HYGIENE', 'WAIT_FLOW', 'PET_CONVENIENCE', 'FACILITY', 'PRICE_VALUE', 'CONTENT_PROGRAM')) OR
        (`scope` = 'BOOTH' AND `category` IN ('CONSULTATION', 'PRODUCT', 'EXPERIENCE', 'PRICE_BENEFIT', 'BOOTH_ENVIRONMENT'))
    ),
    KEY `idx_feedback_tags_scope_category` (`scope`, `category`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- (2) 행사 리뷰 - 방문 1회 단위. 한 사용자가 같은 행사에 리뷰 1건만 남길 수 있다
-- (fair_id, user_id) UNIQUE. 자유서술 텍스트·별점은 완전히 제거되었다.
-- =========================================================
CREATE TABLE `fair_reviews` (
    `review_id`        BIGINT      NOT NULL AUTO_INCREMENT,
    `fair_id`          BIGINT      NOT NULL COMMENT '리뷰 대상 fairs.fair_id',
    `user_id`          BIGINT      NOT NULL COMMENT '작성자 users.user_id',
    `companion_type`   VARCHAR(20) NOT NULL COMMENT 'ALONE / WITH_PET / WITH_FAMILY / WITH_FRIEND',
    `visit_purpose`    VARCHAR(20) NOT NULL COMMENT 'SHOPPING / EXPERIENCE / INFO / ETC',
    `would_revisit`    TINYINT(1)  NOT NULL COMMENT '재방문 의향(NPS형 재방문의향 통계에 사용)',
    `created_at`       DATETIME    NOT NULL,
    `updated_at`       DATETIME    NOT NULL,
    CONSTRAINT `PK_FAIR_REVIEWS` PRIMARY KEY (`review_id`),
    CONSTRAINT `UK_FAIR_REVIEWS_FAIR_USER` UNIQUE (`fair_id`, `user_id`),
    CONSTRAINT `CK_FAIR_REVIEWS_COMPANION_TYPE` CHECK (`companion_type` IN ('ALONE', 'WITH_PET', 'WITH_FAMILY', 'WITH_FRIEND')),
    CONSTRAINT `CK_FAIR_REVIEWS_VISIT_PURPOSE` CHECK (`visit_purpose` IN ('SHOPPING', 'EXPERIENCE', 'INFO', 'ETC')),
    KEY `idx_fair_reviews_fair_created` (`fair_id`, `created_at`, `review_id`),
    KEY `idx_fair_reviews_user_created` (`user_id`, `created_at`, `review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- (3) 행사 전체용 태그 선택 - 리뷰 1건이 고른 scope=FAIR 태그들(다중 선택)
-- =========================================================
CREATE TABLE `fair_review_tag_selections` (
    `review_id` BIGINT NOT NULL COMMENT 'fair_reviews.review_id',
    `tag_id`    BIGINT NOT NULL COMMENT 'feedback_tags.tag_id (scope=FAIR)',
    CONSTRAINT `PK_FAIR_REVIEW_TAG_SELECTIONS` PRIMARY KEY (`review_id`, `tag_id`),
    KEY `idx_fair_review_tag_selections_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- (4) 부스별 평가 - 리뷰 1건에 딸린 부스 평가(마법사에서 최대 3개 부스 선택 후 반복 작성).
-- 한 사용자가 같은 부스에 평가를 여러 번 남기지 못하도록 (booth_id, user_id) UNIQUE.
-- fair_id/business_id/user_id는 booth_visits와 같은 이유로 통계 조회 편의를 위해
-- 비정규화해서 같이 저장한다.
-- =========================================================
CREATE TABLE `booth_feedbacks` (
    `booth_feedback_id`  BIGINT      NOT NULL AUTO_INCREMENT,
    `review_id`          BIGINT      NOT NULL COMMENT '소속 리뷰 fair_reviews.review_id',
    `booth_id`           BIGINT      NOT NULL COMMENT '평가 대상 booth.booth_id',
    `fair_id`            BIGINT      NOT NULL COMMENT 'fairs.fair_id. 통계용 비정규화',
    `business_id`        BIGINT      NOT NULL COMMENT 'business.business_id. 통계용 비정규화',
    `user_id`            BIGINT      NOT NULL COMMENT 'users.user_id. 통계용 비정규화(review_id로도 알 수 있음)',
    `purchase_behavior`  VARCHAR(20) NULL     COMMENT 'PURCHASED / FOLLOWED_SNS / LOOKED_ONLY. 응답을 건너뛸 수 있어 NULL 허용',
    `created_at`         DATETIME    NOT NULL,
    `updated_at`         DATETIME    NOT NULL,
    CONSTRAINT `PK_BOOTH_FEEDBACKS` PRIMARY KEY (`booth_feedback_id`),
    CONSTRAINT `UK_BOOTH_FEEDBACKS_BOOTH_USER` UNIQUE (`booth_id`, `user_id`),
    CONSTRAINT `CK_BOOTH_FEEDBACKS_PURCHASE_BEHAVIOR` CHECK (`purchase_behavior` IN ('PURCHASED', 'FOLLOWED_SNS', 'LOOKED_ONLY')),
    KEY `idx_booth_feedbacks_review` (`review_id`),
    KEY `idx_booth_feedbacks_fair` (`fair_id`),
    KEY `idx_booth_feedbacks_business` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- (5) 부스용 태그 선택 - 부스 평가 1건이 고른 scope=BOOTH 태그들(다중 선택)
-- =========================================================
CREATE TABLE `booth_feedback_selections` (
    `booth_feedback_id` BIGINT NOT NULL COMMENT 'booth_feedbacks.booth_feedback_id',
    `tag_id`             BIGINT NOT NULL COMMENT 'feedback_tags.tag_id (scope=BOOTH)',
    CONSTRAINT `PK_BOOTH_FEEDBACK_SELECTIONS` PRIMARY KEY (`booth_feedback_id`, `tag_id`),
    KEY `idx_booth_feedback_selections_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 시드 데이터 - scope=FAIR(행사 전체용, 7개 카테고리)
-- =========================================================
INSERT INTO `feedback_tags` (`scope`, `category`, `sentiment`, `label`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES
-- 안내/운영
('FAIR', 'GUIDE_OPERATION', 'POSITIVE', '안내가 친절해요', 1, 1, NOW(), NOW()),
('FAIR', 'GUIDE_OPERATION', 'POSITIVE', '동선 안내가 명확해요', 2, 1, NOW(), NOW()),
('FAIR', 'GUIDE_OPERATION', 'POSITIVE', '입장이 빨라요', 3, 1, NOW(), NOW()),
('FAIR', 'GUIDE_OPERATION', 'POSITIVE', '사전 정보와 일치해요', 4, 1, NOW(), NOW()),
('FAIR', 'GUIDE_OPERATION', 'NEGATIVE', '안내판이 부족해요', 5, 1, NOW(), NOW()),
('FAIR', 'GUIDE_OPERATION', 'NEGATIVE', '입장 절차가 복잡해요', 6, 1, NOW(), NOW()),
('FAIR', 'GUIDE_OPERATION', 'NEGATIVE', '사전 안내와 달라요', 7, 1, NOW(), NOW()),
-- 안전/위생
('FAIR', 'SAFETY_HYGIENE', 'POSITIVE', '행사장이 청결해요', 1, 1, NOW(), NOW()),
('FAIR', 'SAFETY_HYGIENE', 'POSITIVE', '반려동물 안전관리가 잘돼있어요', 2, 1, NOW(), NOW()),
('FAIR', 'SAFETY_HYGIENE', 'POSITIVE', '응급상황 대응이 빨라요', 3, 1, NOW(), NOW()),
('FAIR', 'SAFETY_HYGIENE', 'NEGATIVE', '위생이 아쉬워요', 4, 1, NOW(), NOW()),
('FAIR', 'SAFETY_HYGIENE', 'NEGATIVE', '동물 간 마찰 관리가 부족해요', 5, 1, NOW(), NOW()),
('FAIR', 'SAFETY_HYGIENE', 'NEGATIVE', '환기가 안 돼요', 6, 1, NOW(), NOW()),
-- 대기/동선
('FAIR', 'WAIT_FLOW', 'POSITIVE', '대기시간이 짧아요', 1, 1, NOW(), NOW()),
('FAIR', 'WAIT_FLOW', 'POSITIVE', '줄 관리가 잘 돼요', 2, 1, NOW(), NOW()),
('FAIR', 'WAIT_FLOW', 'POSITIVE', '동선이 넓어요', 3, 1, NOW(), NOW()),
('FAIR', 'WAIT_FLOW', 'NEGATIVE', '대기시간이 길어요', 4, 1, NOW(), NOW()),
('FAIR', 'WAIT_FLOW', 'NEGATIVE', '너무 붐벼요', 5, 1, NOW(), NOW()),
('FAIR', 'WAIT_FLOW', 'NEGATIVE', '동선이 복잡해요', 6, 1, NOW(), NOW()),
-- 반려동물 동반 편의성
('FAIR', 'PET_CONVENIENCE', 'POSITIVE', '반려동물 동반이 편해요', 1, 1, NOW(), NOW()),
('FAIR', 'PET_CONVENIENCE', 'POSITIVE', '급수공간이 있어요', 2, 1, NOW(), NOW()),
('FAIR', 'PET_CONVENIENCE', 'POSITIVE', '안전하게 배려해줘요', 3, 1, NOW(), NOW()),
('FAIR', 'PET_CONVENIENCE', 'POSITIVE', '다른 동물과 안전하게 분리돼요', 4, 1, NOW(), NOW()),
('FAIR', 'PET_CONVENIENCE', 'NEGATIVE', '우리 아이가 스트레스 받아 보였어요', 5, 1, NOW(), NOW()),
('FAIR', 'PET_CONVENIENCE', 'NEGATIVE', '소음이 심해요', 6, 1, NOW(), NOW()),
('FAIR', 'PET_CONVENIENCE', 'NEGATIVE', '안전장치가 부족해요', 7, 1, NOW(), NOW()),
-- 시설/편의
('FAIR', 'FACILITY', 'POSITIVE', '주차가 편리해요', 1, 1, NOW(), NOW()),
('FAIR', 'FACILITY', 'POSITIVE', '화장실이 깨끗해요', 2, 1, NOW(), NOW()),
('FAIR', 'FACILITY', 'POSITIVE', '휴게공간이 충분해요', 3, 1, NOW(), NOW()),
('FAIR', 'FACILITY', 'POSITIVE', '펫 놀이터가 잘돼있어요', 4, 1, NOW(), NOW()),
('FAIR', 'FACILITY', 'NEGATIVE', '주차가 불편해요', 5, 1, NOW(), NOW()),
('FAIR', 'FACILITY', 'NEGATIVE', '휴게공간이 부족해요', 6, 1, NOW(), NOW()),
('FAIR', 'FACILITY', 'NEGATIVE', '화장실이 부족해요', 7, 1, NOW(), NOW()),
-- 가격/가치
('FAIR', 'PRICE_VALUE', 'POSITIVE', '입장료가 합리적이에요', 1, 1, NOW(), NOW()),
('FAIR', 'PRICE_VALUE', 'POSITIVE', '혜택이 알차요', 2, 1, NOW(), NOW()),
('FAIR', 'PRICE_VALUE', 'NEGATIVE', '입장료 대비 아쉬워요', 3, 1, NOW(), NOW()),
-- 부대행사/콘텐츠
('FAIR', 'CONTENT_PROGRAM', 'POSITIVE', '공연·이벤트가 알차요', 1, 1, NOW(), NOW()),
('FAIR', 'CONTENT_PROGRAM', 'POSITIVE', '포토존이 좋아요', 2, 1, NOW(), NOW()),
('FAIR', 'CONTENT_PROGRAM', 'POSITIVE', '세미나가 유익해요', 3, 1, NOW(), NOW()),
('FAIR', 'CONTENT_PROGRAM', 'NEGATIVE', '부대행사가 부실해요', 4, 1, NOW(), NOW()),
('FAIR', 'CONTENT_PROGRAM', 'NEGATIVE', '볼거리가 부족해요', 5, 1, NOW(), NOW());


-- =========================================================
-- 시드 데이터 - scope=BOOTH(부스별용, 5개 카테고리)
-- 행사 전체용과 겹치는 개념(대기/혼잡 등)은 문구를 다르게 둬서 두 scope가 섞이지 않게 한다.
-- =========================================================
INSERT INTO `feedback_tags` (`scope`, `category`, `sentiment`, `label`, `sort_order`, `is_active`, `created_at`, `updated_at`) VALUES
-- 상담/응대
('BOOTH', 'CONSULTATION', 'POSITIVE', '설명이 친절해요', 1, 1, NOW(), NOW()),
('BOOTH', 'CONSULTATION', 'POSITIVE', '질문에 성실히 답해줘요', 2, 1, NOW(), NOW()),
('BOOTH', 'CONSULTATION', 'NEGATIVE', '응대가 불친절해요', 3, 1, NOW(), NOW()),
('BOOTH', 'CONSULTATION', 'NEGATIVE', '설명이 부족해요', 4, 1, NOW(), NOW()),
-- 상품/구성
('BOOTH', 'PRODUCT', 'POSITIVE', '상품 구성이 알차요', 1, 1, NOW(), NOW()),
('BOOTH', 'PRODUCT', 'POSITIVE', '신제품이 다양해요', 2, 1, NOW(), NOW()),
('BOOTH', 'PRODUCT', 'NEGATIVE', '구성이 부실해요', 3, 1, NOW(), NOW()),
('BOOTH', 'PRODUCT', 'NEGATIVE', '품절이 빨라요', 4, 1, NOW(), NOW()),
-- 체험/시연
('BOOTH', 'EXPERIENCE', 'POSITIVE', '체험이 만족스러워요', 1, 1, NOW(), NOW()),
('BOOTH', 'EXPERIENCE', 'POSITIVE', '시연이 유익해요', 2, 1, NOW(), NOW()),
('BOOTH', 'EXPERIENCE', 'NEGATIVE', '체험 순서가 오래 걸려요', 3, 1, NOW(), NOW()),
('BOOTH', 'EXPERIENCE', 'NEGATIVE', '시연이 부실해요', 4, 1, NOW(), NOW()),
-- 가격/혜택
('BOOTH', 'PRICE_BENEFIT', 'POSITIVE', '가격이 합리적이에요', 1, 1, NOW(), NOW()),
('BOOTH', 'PRICE_BENEFIT', 'POSITIVE', '박람회 특가·혜택이 좋아요', 2, 1, NOW(), NOW()),
('BOOTH', 'PRICE_BENEFIT', 'NEGATIVE', '가격이 비싸요', 3, 1, NOW(), NOW()),
('BOOTH', 'PRICE_BENEFIT', 'NEGATIVE', '혜택이 아쉬워요', 4, 1, NOW(), NOW()),
-- 부스 환경
('BOOTH', 'BOOTH_ENVIRONMENT', 'POSITIVE', '부스가 깨끗해요', 1, 1, NOW(), NOW()),
('BOOTH', 'BOOTH_ENVIRONMENT', 'POSITIVE', '제품 진열이 보기 좋아요', 2, 1, NOW(), NOW()),
('BOOTH', 'BOOTH_ENVIRONMENT', 'POSITIVE', '동선이 편해요', 3, 1, NOW(), NOW()),
('BOOTH', 'BOOTH_ENVIRONMENT', 'NEGATIVE', '부스가 좁아요', 4, 1, NOW(), NOW()),
('BOOTH', 'BOOTH_ENVIRONMENT', 'NEGATIVE', '혼잡해요', 5, 1, NOW(), NOW()),
('BOOTH', 'BOOTH_ENVIRONMENT', 'NEGATIVE', '진열이 산만해요', 6, 1, NOW(), NOW());
