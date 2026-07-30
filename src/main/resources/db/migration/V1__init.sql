-- =========================================================
-- V1__init.sql
-- petopia 초기 스키마
--
-- 구성
--   (1) 회원 · 인증
--   (2) 행사 (신청 · 심사 · 공간)
--   (3) 업체 · 부스
--   (4) 예약 · 입장
--   (5) 행사 담당자 배정
--   (6) 결제 · 정산
--   (7) 알림 · 감사
--   (8) 뷰
--
-- 주의: 이 파일은 한 번 적용되면 수정하지 않는다. 변경은 V2 이후 파일로 한다.
-- =========================================================


-- =========================================================
-- (1) 회원 · 인증
-- =========================================================

CREATE TABLE `users` (
    `user_id`        BIGINT       NOT NULL AUTO_INCREMENT,
    `email`          VARCHAR(255) NOT NULL COMMENT '소셜/이메일 가입 공통',
    `password_hash`  VARCHAR(255) NULL     COMMENT '이메일 가입자·관리자만 보유',
    `nickname`       VARCHAR(50)  NOT NULL,
    `age`            INT          NOT NULL,
    `phone`          VARCHAR(20)  NOT NULL COMMENT '010 등 앞자리 0 보존을 위해 문자열 저장',
    `gender`         VARCHAR(20)  NOT NULL,
    `address`        VARCHAR(300) NOT NULL,
    `agreed_terms`   BOOLEAN      NOT NULL COMMENT 'TRUE여야 가입',
    `agreed_privacy` BOOLEAN      NOT NULL COMMENT 'TRUE여야 가입',
    `role`           VARCHAR(20)  NOT NULL COMMENT 'USER / VENDOR / EVENT_ADMIN / SUPER_ADMIN',
    `status`         VARCHAR(20)  NOT NULL COMMENT 'ACTIVE / INACTIVE',
    `email_verified` BOOLEAN      NOT NULL COMMENT '가입 시 이메일 인증',
    `created_at`     DATETIME     NOT NULL COMMENT '가입 및 동의 일시',
    `updated_at`     DATETIME     NULL,
    CONSTRAINT `PK_USERS`       PRIMARY KEY (`user_id`),
    CONSTRAINT `UK_USERS_EMAIL` UNIQUE (`email`),
    KEY `idx_users_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `user_social_accounts` (
    `social_id`    BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT       NOT NULL COMMENT 'users.user_id',
    `provider`     VARCHAR(20)  NOT NULL COMMENT 'GOOGLE / GITHUB',
    `oauth_id`     VARCHAR(255) NOT NULL COMMENT '소셜 제공자가 준 고유 ID',
    `connected_at` DATETIME     NOT NULL COMMENT '소셜 계정 연결 일시',
    CONSTRAINT `PK_USER_SOCIAL_ACCOUNTS`  PRIMARY KEY (`social_id`),
    CONSTRAINT `UK_SOCIAL_PROVIDER_OAUTH` UNIQUE (`provider`, `oauth_id`),
    KEY `idx_social_account_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `refresh_tokens` (
    `refresh_token_id` BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT   NOT NULL COMMENT 'users.user_id',
    `token_hash`       CHAR(64) NOT NULL COMMENT 'SHA-256 해시는 항상 64자',
    `expires_at`       DATETIME NOT NULL COMMENT '발급 후 2주',
    `revoked_at`       DATETIME NULL     COMMENT '로그아웃·계정정지 시각',
    `created_at`       DATETIME NOT NULL,
    CONSTRAINT `PK_REFRESH_TOKENS`     PRIMARY KEY (`refresh_token_id`),
    CONSTRAINT `UK_REFRESH_TOKEN_HASH` UNIQUE (`token_hash`),
    KEY `idx_refresh_token_user_expires` (`user_id`, `expires_at`),
    KEY `idx_refresh_token_expires`      (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `user_tokens` (
    `token_id`   BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT      NOT NULL COMMENT 'users.user_id',
    `token_hash` CHAR(64)    NOT NULL COMMENT 'SHA-256 해시는 항상 64자',
    `purpose`    VARCHAR(30) NOT NULL COMMENT 'PASSWORD_RESET / EMAIL_VERIFY 등',
    `used_at`    DATETIME    NULL     COMMENT '사용 완료 시각. 재사용 차단용',
    `created_at` DATETIME    NOT NULL,
    `expires_at` DATETIME    NOT NULL,
    CONSTRAINT `PK_USER_TOKENS`     PRIMARY KEY (`token_id`),
    CONSTRAINT `UK_USER_TOKEN_HASH` UNIQUE (`token_hash`),
    KEY `idx_user_token_user_purpose_expires` (`user_id`, `purpose`, `expires_at`),
    KEY `idx_user_token_expires`              (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `pets` (
    `pet_id`      BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT 'users.user_id',
    `name`        VARCHAR(50)  NOT NULL,
    `species`     VARCHAR(20)  NOT NULL,
    `breed`       VARCHAR(50)  NULL,
    `age`         INT          NULL,
    `gender`      VARCHAR(20)  NULL COMMENT 'MALE / FEMALE',
    `is_neutered` BOOLEAN      NULL COMMENT '중성화 여부',
    `image_url`   VARCHAR(500) NULL,
    `created_at`  DATETIME     NOT NULL,
    CONSTRAINT `PK_PETS` PRIMARY KEY (`pet_id`),
    KEY `idx_pets_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- (2) 행사 (신청 · 심사 · 공간)
-- =========================================================

CREATE TABLE `fairs` (
    `fair_id`                           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '신청서 PK 겸 행사 PK',
    `applicant_user_id`                 BIGINT       NOT NULL COMMENT '신청한 일반 사용자 users.user_id',

    `name`                              VARCHAR(200) NOT NULL COMMENT '행사명',
    `description`                       TEXT         NULL     COMMENT '행사 소개',
    `category`                          VARCHAR(50)  NULL     COMMENT 'DOG / CAT / ETC',
    `poster_image_url`                  VARCHAR(500) NULL     COMMENT '포스터·배너',
    `notice_text`                       TEXT         NULL     COMMENT '유의사항',
    `place_name`                        VARCHAR(200) NULL     COMMENT '장소명. 마스터 테이블 없이 행사에 직접 종속',
    `address`                           VARCHAR(300) NULL,
    `indoor_outdoor`                    VARCHAR(10)  NULL     COMMENT 'INDOOR / OUTDOOR',

    `vendor_recruit_start_date`         DATE         NULL     COMMENT '참가업체 모집 시작',
    `vendor_recruit_end_date`           DATE         NULL     COMMENT '참가업체 모집 종료',
    `reservation_start_date`            DATE         NULL     COMMENT '사전예약 시작',
    `reservation_end_date`              DATE         NULL     COMMENT '사전예약 종료',
    `operation_start_date`              DATE         NULL     COMMENT '행사 운영 시작',
    `operation_end_date`                DATE         NULL     COMMENT '행사 운영 종료',

    `reservation_fee`                   BIGINT       NOT NULL DEFAULT 0 COMMENT '관람객 예약금(원). 0이면 무료',
    `reservation_cancel_deadline_hours` INT          NULL     COMMENT '예약 취소 가능 기한(시간)',
    `reservation_change_deadline_hours` INT          NULL     COMMENT '예약 변경 가능 기한(시간)',

    `manager_name`                      VARCHAR(50)  NOT NULL COMMENT '행사 담당자 이름',
    `manager_phone`                     VARCHAR(20)  NULL     COMMENT '심사 중 통화 확인용',
    `manager_email`                     VARCHAR(100) NOT NULL COMMENT '승인 시 이 주소로 EVENT_ADMIN 계정 발송',

    `status`                            VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED'
        COMMENT 'RECEIVED / REJECTED / PAYMENT_PENDING / EXPIRED / PREPARING / IN_PROGRESS / ENDED',
    `reject_reason`                     TEXT         NULL     COMMENT '반려 사유',
    `reviewed_by`                       BIGINT       NULL     COMMENT '심사한 SUPER_ADMIN users.user_id',
    `reviewed_at`                       DATETIME     NULL     COMMENT '심사 처리 일시',

    `payment_due_at`                    DATETIME     NULL     COMMENT '개설비 결제 기한. 초과 시 EXPIRED',
    `public_scheduled_at`               DATETIME     NULL     COMMENT '공개 예정 일시',
    `published_at`                      DATETIME     NULL     COMMENT '실제 공개된 일시',
    `canceled_at`                       DATETIME     NULL     COMMENT '취소 승인 일시. NULL이면 취소 아님',

    `submitted_snapshot`                JSON         NULL     COMMENT '신청 당시 내용 원본. 분쟁 대비용',

    `created_at`                        DATETIME     NOT NULL COMMENT '신청서 제출 일시',
    `updated_at`                        DATETIME     NOT NULL,
    CONSTRAINT `PK_FAIRS` PRIMARY KEY (`fair_id`),
    KEY `idx_fairs_applicant_created` (`applicant_user_id`, `created_at`),
    KEY `idx_fairs_status_created`    (`status`, `created_at`),
    KEY `idx_fairs_status_operation`  (`status`, `operation_start_date`, `operation_end_date`),
    KEY `idx_fairs_reviewed_by`       (`reviewed_by`),
    KEY `idx_fairs_payment_due_at`    (`payment_due_at`),
    KEY `idx_fairs_published_at`      (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `fair_dates` (
    `fair_date_id`     BIGINT   NOT NULL AUTO_INCREMENT,
    `fair_id`          BIGINT   NOT NULL COMMENT 'fairs.fair_id',
    `operation_date`   DATE     NOT NULL COMMENT '운영 날짜',
    `capacity`         INT      NOT NULL COMMENT '해당 날짜 예약 정원',
    `entry_start_time` TIME     NOT NULL COMMENT '입장 가능 시작',
    `entry_end_time`   TIME     NOT NULL COMMENT '입장 가능 종료',
    `created_at`       DATETIME NOT NULL,
    `updated_at`       DATETIME NOT NULL,
    CONSTRAINT `PK_FAIR_DATES`          PRIMARY KEY (`fair_date_id`),
    CONSTRAINT `UK_FAIR_DATE_FAIR_DATE` UNIQUE (`fair_id`, `operation_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `fair_cancel_requests` (
    `fair_cancel_request_id` BIGINT      NOT NULL AUTO_INCREMENT,
    `fair_id`                BIGINT      NOT NULL COMMENT 'fairs.fair_id',
    `requested_by`           BIGINT      NOT NULL COMMENT '신청한 EVENT_ADMIN users.user_id',
    `reason`                 TEXT        NOT NULL COMMENT '취소 신청 사유',
    `status`                 VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
    `reject_reason`          TEXT        NULL,
    `reviewed_by`            BIGINT      NULL     COMMENT 'SUPER_ADMIN users.user_id',
    `reviewed_at`            DATETIME    NULL,
    `created_at`             DATETIME    NOT NULL,
    CONSTRAINT `PK_FAIR_CANCEL_REQUESTS` PRIMARY KEY (`fair_cancel_request_id`),
    KEY `idx_fair_cancel_fair_status_created` (`fair_id`, `status`, `created_at`),
    KEY `idx_fair_cancel_requested_by`        (`requested_by`),
    KEY `idx_fair_cancel_reviewed_by`         (`reviewed_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `halls` (
    `hall_id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `fair_id`              BIGINT       NOT NULL COMMENT 'fairs.fair_id',
    `name`                 VARCHAR(50)  NOT NULL COMMENT 'A홀, 1홀 등',
    `floor_plan_image_url` VARCHAR(500) NULL     COMMENT '배치도 이미지',
    `created_at`           DATETIME     NOT NULL,
    `updated_at`           DATETIME     NOT NULL,
    CONSTRAINT `PK_HALLS` PRIMARY KEY (`hall_id`),
    KEY `idx_halls_fair` (`fair_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `booth_slots` (
    `booth_slot_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `hall_id`       BIGINT       NOT NULL COMMENT 'halls.hall_id',
    `slot_number`   VARCHAR(20)  NOT NULL COMMENT '부스 번호. 예: A-01',
    `pos_x`         DECIMAL(6,4) NOT NULL COMMENT '도면 대비 비율 좌표 0~1',
    `pos_y`         DECIMAL(6,4) NOT NULL COMMENT '도면 대비 비율 좌표 0~1',
    `width`         DECIMAL(6,4) NOT NULL COMMENT '도면 대비 가로 비율 0~1',
    `height`        DECIMAL(6,4) NOT NULL COMMENT '도면 대비 세로 비율 0~1',
    `price`         BIGINT       NOT NULL COMMENT '기본 가격(원)',
    `is_active`     BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '사용 여부',
    `memo`          VARCHAR(200) NULL     COMMENT '전기 제공 등',
    `locked_at`     DATETIME     NULL     COMMENT '위치·번호·가격 변경 제한 시점',
    `created_at`    DATETIME     NOT NULL,
    `updated_at`    DATETIME     NOT NULL,
    CONSTRAINT `PK_BOOTH_SLOTS`            PRIMARY KEY (`booth_slot_id`),
    CONSTRAINT `UK_BOOTH_SLOT_HALL_NUMBER` UNIQUE (`hall_id`, `slot_number`),
    KEY `idx_booth_slots_hall_active` (`hall_id`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- (3) 업체 · 부스
-- =========================================================

CREATE TABLE `business` (
    `business_id`   BIGINT       NOT NULL AUTO_INCREMENT,
    `owner_id`      BIGINT       NOT NULL COMMENT 'users.user_id',
    `name`          VARCHAR(100) NOT NULL,
    `ceo_name`      VARCHAR(50)  NOT NULL COMMENT '국세청 진위확인 API 필수값',
    `biz_reg_no`    VARCHAR(20)  NOT NULL COMMENT '사업자등록번호',
    `start_date`    DATE         NOT NULL COMMENT '국세청 진위확인 API 필수값',
    `address`       VARCHAR(300) NOT NULL,
    `phone`         VARCHAR(20)  NOT NULL,
    `website`       VARCHAR(255) NULL,
    `verify_status` VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / VERIFIED / INVALID / RETRY_NEEDED',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `PK_BUSINESS`            PRIMARY KEY (`business_id`),
    CONSTRAINT `UK_BUSINESS_BIZ_REG_NO` UNIQUE (`biz_reg_no`),
    KEY `idx_business_owner`          (`owner_id`),
    KEY `idx_business_verify_created` (`verify_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `application` (
    `application_id`  BIGINT       NOT NULL AUTO_INCREMENT,
    `business_id`     BIGINT       NOT NULL COMMENT 'business.business_id',
    `fair_id`         BIGINT       NOT NULL COMMENT 'fairs.fair_id',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW'
        COMMENT 'PENDING_REVIEW / REJECTED / PAYMENT_PENDING / CONFIRMED / CANCELED',
    `reject_reason`   VARCHAR(500) NULL,
    `final_price`     BIGINT       NULL COMMENT '승인 시 확정. 선택 슬롯 가격 합계',
    `submitted_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `reviewed_at`     DATETIME     NULL,
    `payment_due_at`  DATETIME     NULL,
    `active_key`      VARCHAR(50)  AS (
        CASE WHEN `status` IN ('PENDING_REVIEW', 'PAYMENT_PENDING', 'CONFIRMED')
             THEN CONCAT(`business_id`, '_', `fair_id`)
             ELSE NULL END
    ) STORED COMMENT '활성 신청 중복 방지용 계산 컬럼. REJECTED/CANCELED면 NULL',
    CONSTRAINT `PK_APPLICATION`        PRIMARY KEY (`application_id`),
    CONSTRAINT `UK_APPLICATION_ACTIVE` UNIQUE (`active_key`),
    KEY `idx_application_business_status_submitted` (`business_id`, `status`, `submitted_at`),
    KEY `idx_application_fair_status_submitted`     (`fair_id`, `status`, `submitted_at`),
    KEY `idx_application_payment_due_at`            (`payment_due_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `application_form` (
    `application_form_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `application_id`      BIGINT       NOT NULL COMMENT 'application.application_id. 1:1',
    `purpose`             VARCHAR(200) NOT NULL COMMENT '신제품 홍보, 오프라인 판매 등',
    `items_desc`          VARCHAR(500) NOT NULL,
    `manager_name`        VARCHAR(50)  NOT NULL,
    `manager_phone`       VARCHAR(20)  NOT NULL,
    `manager_email`       VARCHAR(100) NOT NULL,
    `agreed_terms`        BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '제출 시 반드시 TRUE',
    `attachment_url`      VARCHAR(500) NULL     COMMENT '증빙서류 zip URL. 선택 입력',
    CONSTRAINT `PK_APPLICATION_FORM`             PRIMARY KEY (`application_form_id`),
    CONSTRAINT `UK_APPLICATION_FORM_APPLICATION` UNIQUE (`application_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `application_slot` (
    `application_slot_id` BIGINT NOT NULL AUTO_INCREMENT,
    `application_id`      BIGINT NOT NULL COMMENT 'application.application_id',
    `booth_slot_id`       BIGINT NOT NULL COMMENT 'booth_slots.booth_slot_id',
    `price_at_selection`  BIGINT NOT NULL COMMENT '신청 시점 가격 스냅샷',
    CONSTRAINT `PK_APPLICATION_SLOT`           PRIMARY KEY (`application_slot_id`),
    CONSTRAINT `UK_APP_SLOT_APPLICATION_SLOT`  UNIQUE (`application_id`, `booth_slot_id`),
    KEY `idx_application_slot_booth` (`booth_slot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `application_cancel_request` (
    `cancel_request_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `application_id`    BIGINT       NOT NULL COMMENT 'application.application_id',
    `reason`            VARCHAR(500) NOT NULL,
    `status`            VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED / APPROVED / REJECTED',
    `requested_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `decided_at`        DATETIME     NULL,
    CONSTRAINT `PK_APPLICATION_CANCEL_REQUEST` PRIMARY KEY (`cancel_request_id`),
    KEY `idx_app_cancel_application`      (`application_id`),
    KEY `idx_app_cancel_status_requested` (`status`, `requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `booth` (
    `booth_id`       BIGINT        NOT NULL AUTO_INCREMENT,
    `application_id` BIGINT        NOT NULL COMMENT 'application.application_id. 1:1',
    `business_id`    BIGINT        NOT NULL COMMENT 'business.business_id. 조회 편의용 비정규화',
    `name`           VARCHAR(100)  NULL,
    `image_url`      VARCHAR(500)  NULL     COMMENT '업체 로고·부스 배너',
    `intro`          VARCHAR(1000) NULL,
    `category`       VARCHAR(50)   NULL     COMMENT '사료/간식, 미용, 훈련, 굿즈 등',
    `target_animal`  VARCHAR(10)   NULL     COMMENT 'DOG / CAT / ETC',
    `confirmed_at`   DATETIME      NOT NULL,
    CONSTRAINT `PK_BOOTH`             PRIMARY KEY (`booth_id`),
    CONSTRAINT `UK_BOOTH_APPLICATION` UNIQUE (`application_id`),
    KEY `idx_booth_business` (`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `booth_item` (
    `booth_item_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `booth_id`      BIGINT       NOT NULL COMMENT 'booth.booth_id',
    `name`          VARCHAR(100) NOT NULL,
    `type`          VARCHAR(20)  NOT NULL COMMENT 'PRODUCT / EVENT / SAMPLE',
    `image_url`     VARCHAR(500) NULL,
    `note`          VARCHAR(255) NULL,
    CONSTRAINT `PK_BOOTH_ITEM` PRIMARY KEY (`booth_item_id`),
    KEY `idx_booth_item_booth` (`booth_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `recruit_notice` (
    `recruit_notice_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `writer_id`         BIGINT       NOT NULL COMMENT '작성 EVENT_ADMIN users.user_id',
    `fair_id`           BIGINT       NOT NULL COMMENT 'fairs.fair_id. 행사당 1건',
    `title`             VARCHAR(200) NOT NULL,
    `content`           TEXT         NOT NULL COMMENT '모집요강·지원자격·문의처',
    `image_url`         VARCHAR(500) NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NULL,
    `recruit_deadline`  DATETIME     NOT NULL COMMENT '조기마감 가능',
    CONSTRAINT `PK_RECRUIT_NOTICE`       PRIMARY KEY (`recruit_notice_id`),
    CONSTRAINT `UK_RECRUIT_NOTICE_FAIR`  UNIQUE (`fair_id`),
    KEY `idx_recruit_notice_writer`   (`writer_id`),
    KEY `idx_recruit_notice_deadline` (`recruit_deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- (4) 예약 · 입장
-- =========================================================

CREATE TABLE `reservations` (
    `reservation_id`     BIGINT       NOT NULL AUTO_INCREMENT,
    `reservation_no`     VARCHAR(20)  NOT NULL COMMENT '사용자에게 보여주는 예약번호',
    `fair_id`            BIGINT       NOT NULL COMMENT 'fairs.fair_id',
    `user_id`            BIGINT       NOT NULL COMMENT 'users.user_id',
    `visit_date`         DATE         NOT NULL,
    `status`             VARCHAR(20)  NOT NULL COMMENT 'PENDING_PAYMENT / CONFIRMED / CHECKED_IN / EXPIRED / CANCELED',
    `reservation_amount` BIGINT       NOT NULL DEFAULT 0,
    `reserver_name`      VARCHAR(50)  NOT NULL,
    `reserver_phone`     VARCHAR(20)  NULL,
    `reserver_email`     VARCHAR(255) NULL,
    `channel`            VARCHAR(10)  NOT NULL DEFAULT 'ONLINE',
    `agreed_terms`       BOOLEAN      NOT NULL,
    `agreed_privacy`     BOOLEAN      NOT NULL,
    `agreed_marketing`   BOOLEAN      NOT NULL DEFAULT FALSE,
    `reserved_at`        DATETIME     NULL     COMMENT '예약 확정 시각',
    `canceled_at`        DATETIME     NULL,
    `cancel_reason`      VARCHAR(500) NULL,
    `canceled_by`        BIGINT       NULL     COMMENT 'users.user_id. 본인 취소와 관리자 취소 구분',
    `created_at`         DATETIME     NOT NULL,
    `updated_at`         DATETIME     NOT NULL,
    CONSTRAINT `PK_RESERVATIONS`    PRIMARY KEY (`reservation_id`),
    CONSTRAINT `UK_RESERVATION_NO`  UNIQUE (`reservation_no`),
    KEY `idx_reservation_fair_status_visit`   (`fair_id`, `status`, `visit_date`),
    KEY `idx_reservation_user_status_created` (`user_id`, `status`, `created_at`),
    KEY `idx_reservation_canceled_by`         (`canceled_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `reservation_pets` (
    `reservation_pet_id`    BIGINT       NOT NULL AUTO_INCREMENT,
    `reservation_id`        BIGINT       NOT NULL COMMENT 'reservations.reservation_id',
    `pet_id`                BIGINT       NOT NULL COMMENT 'pets.pet_id',
    `pet_name_snapshot`     VARCHAR(50)  NOT NULL,
    `pet_species_snapshot`  VARCHAR(20)  NOT NULL,
    `pet_breed_snapshot`    VARCHAR(50)  NULL,
    `pet_age_snapshot`      INT          NULL,
    `pet_allergy_snapshot`  VARCHAR(255) NULL COMMENT 'pets에 allergy 컬럼이 없음. 추가 여부 확인 필요',
    `created_at`            DATETIME     NOT NULL,
    CONSTRAINT `PK_RESERVATION_PETS` PRIMARY KEY (`reservation_pet_id`),
    KEY `idx_reservation_pet_reservation` (`reservation_id`),
    KEY `idx_reservation_pet_pet`         (`pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `reservation_histories` (
    `history_id`      BIGINT       NOT NULL AUTO_INCREMENT,
    `reservation_id`  BIGINT       NOT NULL COMMENT 'reservations.reservation_id',
    `action_type`     VARCHAR(30)  NOT NULL,
    `before_data`     JSON         NULL,
    `after_data`      JSON         NULL,
    `changed_by`      BIGINT       NOT NULL COMMENT 'users.user_id',
    `is_admin_action` BOOLEAN      NOT NULL DEFAULT FALSE,
    `change_reason`   VARCHAR(500) NULL,
    `created_at`      DATETIME     NOT NULL,
    CONSTRAINT `PK_RESERVATION_HISTORIES` PRIMARY KEY (`history_id`),
    KEY `idx_reservation_history_reservation_created` (`reservation_id`, `created_at`),
    KEY `idx_reservation_history_changed_by`          (`changed_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `entry_qrs` (
    `entry_qr_id`     BIGINT       NOT NULL AUTO_INCREMENT,
    `reservation_id`  BIGINT       NOT NULL COMMENT 'reservations.reservation_id. 예약 1건 = QR 1개',
    `token_hash`      CHAR(64)     NOT NULL COMMENT 'QR 난수 토큰의 해시. 개인정보 미포함',
    `qr_status`       VARCHAR(20)  NOT NULL COMMENT '기획서는 상태값 폐지 결정. 유지 시 문서 수정 필요',
    `available_from`  DATETIME     NOT NULL,
    `expires_at`      DATETIME     NOT NULL,
    `issued_at`       DATETIME     NOT NULL,
    `revoked_at`      DATETIME     NULL,
    `revoke_reason`   VARCHAR(200) NULL,
    `created_at`      DATETIME     NOT NULL,
    `updated_at`      DATETIME     NOT NULL,
    CONSTRAINT `PK_ENTRY_QRS`             PRIMARY KEY (`entry_qr_id`),
    CONSTRAINT `UK_ENTRY_QR_TOKEN`        UNIQUE (`token_hash`),
    CONSTRAINT `UK_ENTRY_QR_RESERVATION`  UNIQUE (`reservation_id`),
    KEY `idx_entry_qr_status_expires` (`qr_status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `entry_records` (
    `entry_record_id`      BIGINT      NOT NULL AUTO_INCREMENT,
    `fair_id`              BIGINT      NOT NULL COMMENT 'fairs.fair_id. 통계용 비정규화',
    `reservation_id`       BIGINT      NOT NULL COMMENT 'reservations.reservation_id. 예약당 1건',
    `user_id`              BIGINT      NOT NULL COMMENT 'users.user_id. 통계용 비정규화',
    `first_checked_in_at`  DATETIME    NOT NULL COMMENT '최초 입장 시각',
    `last_scanned_at`      DATETIME    NOT NULL COMMENT '재스캔 시 갱신',
    `scan_count`           INT         NOT NULL DEFAULT 1 COMMENT '재스캔 시 증가',
    `first_processed_by`   BIGINT      NULL     COMMENT '처리한 EVENT_ADMIN users.user_id',
    `first_gate_name`      VARCHAR(50) NULL,
    `created_at`           DATETIME    NOT NULL,
    `updated_at`           DATETIME    NOT NULL,
    CONSTRAINT `PK_ENTRY_RECORDS`             PRIMARY KEY (`entry_record_id`),
    CONSTRAINT `UK_ENTRY_RECORD_RESERVATION`  UNIQUE (`reservation_id`),
    KEY `idx_entry_record_fair_checked_in` (`fair_id`, `first_checked_in_at`),
    KEY `idx_entry_record_user`            (`user_id`),
    KEY `idx_entry_record_processed_by`    (`first_processed_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `entry_scan_logs` (
    `scan_log_id`     BIGINT       NOT NULL AUTO_INCREMENT,
    `entry_qr_id`     BIGINT       NULL     COMMENT 'entry_qrs.entry_qr_id. 잘못된 QR이면 NULL',
    `reservation_id`  BIGINT       NULL     COMMENT 'reservations.reservation_id',
    `fair_id`         BIGINT       NOT NULL COMMENT 'fairs.fair_id',
    `scan_type`       VARCHAR(20)  NOT NULL COMMENT 'GATE / BOOTH',
    `result_code`     VARCHAR(40)  NOT NULL COMMENT 'SUCCESS / EXPIRED / NOT_FOUND 등',
    `scanned_at`      DATETIME     NOT NULL,
    `processed_by`    BIGINT       NULL     COMMENT '스캔한 users.user_id',
    `gate_name`       VARCHAR(50)  NULL,
    `device_info`     VARCHAR(200) NULL,
    `created_at`      DATETIME     NOT NULL,
    CONSTRAINT `PK_ENTRY_SCAN_LOGS` PRIMARY KEY (`scan_log_id`),
    KEY `idx_scan_log_fair_scanned`   (`fair_id`, `scanned_at`),
    KEY `idx_scan_log_entry_qr`       (`entry_qr_id`),
    KEY `idx_scan_log_reservation`    (`reservation_id`),
    KEY `idx_scan_log_processed_by`   (`processed_by`),
    KEY `idx_scan_log_result_scanned` (`result_code`, `scanned_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `booth_visits` (
    `booth_visit_id`   BIGINT   NOT NULL AUTO_INCREMENT,
    `fair_id`          BIGINT   NOT NULL COMMENT 'fairs.fair_id. 통계용 비정규화',
    `booth_id`         BIGINT   NOT NULL COMMENT 'booth.booth_id',
    `business_id`      BIGINT   NOT NULL COMMENT 'business.business_id. 통계용 비정규화',
    `user_id`          BIGINT   NOT NULL COMMENT 'users.user_id',
    `reservation_id`   BIGINT   NOT NULL COMMENT 'reservations.reservation_id',
    `first_visited_at` DATETIME NOT NULL,
    `last_visited_at`  DATETIME NOT NULL COMMENT '재방문 시 갱신',
    `visit_count`      INT      NOT NULL DEFAULT 1 COMMENT '재방문 시 증가',
    `created_at`       DATETIME NOT NULL,
    `updated_at`       DATETIME NOT NULL,
    CONSTRAINT `PK_BOOTH_VISITS`                   PRIMARY KEY (`booth_visit_id`),
    CONSTRAINT `UK_BOOTH_VISIT_RESERVATION_BOOTH`  UNIQUE (`reservation_id`, `booth_id`),
    KEY `idx_booth_visit_fair_first` (`fair_id`, `first_visited_at`),
    KEY `idx_booth_visit_booth`      (`booth_id`),
    KEY `idx_booth_visit_business`   (`business_id`),
    KEY `idx_booth_visit_user`       (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- (5) 행사 담당자 배정
-- fairs 를 참조하므로 행사 블록 뒤에 둔다.
-- =========================================================

CREATE TABLE `fair_admin_assignments` (
    `fair_admin_assignment_id` BIGINT   NOT NULL AUTO_INCREMENT,
    `admin_user_id`            BIGINT   NOT NULL COMMENT '승인 시 발급된 EVENT_ADMIN users.user_id',
    `requester_user_id`        BIGINT   NOT NULL COMMENT '행사를 신청한 users.user_id',
    `fair_id`                  BIGINT   NOT NULL COMMENT '담당 fairs.fair_id',
    `assigned_at`              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT `PK_FAIR_ADMIN_ASSIGNMENTS` PRIMARY KEY (`fair_admin_assignment_id`),
    CONSTRAINT `UK_FAIR_ADMIN_FAIR`        UNIQUE (`fair_id`),
    KEY `idx_fair_admin_admin_user` (`admin_user_id`),
    KEY `idx_fair_admin_requester`  (`requester_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- (6) 결제 · 정산
-- =========================================================

CREATE TABLE `payment` (
    `payment_id`       BIGINT       NOT NULL AUTO_INCREMENT,
    `payment_type`     VARCHAR(30)  NOT NULL COMMENT 'RESERVATION_DEPOSIT / VENDOR_FEE / FAIR_OPENING_FEE',
    `amount`           BIGINT       NOT NULL COMMENT '원화 정수, 0 이상',
    `status`           VARCHAR(20)  NOT NULL COMMENT 'PENDING / COMPLETED / FAILED / CANCELED / EXPIRED',
    `method`           VARCHAR(20)  NOT NULL COMMENT 'MVP는 MOCK 고정',
    `idempotency_key`  VARCHAR(100) NOT NULL COMMENT '중복 결제 요청 차단 키',
    `paid_at`          DATETIME     NULL     COMMENT 'COMPLETED 전환 시각',
    `created_at`       DATETIME     NOT NULL,
    `updated_at`       DATETIME     NOT NULL,
    `fair_id`          BIGINT       NOT NULL COMMENT 'fairs.fair_id. 세 유형 모두 필수',
    `business_id`      BIGINT       NULL     COMMENT 'business.business_id. VENDOR_FEE만 값 존재',
    `payer_user_id`    BIGINT       NULL     COMMENT '결제한 users.user_id',
    `reservation_id`   BIGINT       NULL     COMMENT 'reservations.reservation_id. RESERVATION_DEPOSIT만',
    `application_id`   BIGINT       NULL     COMMENT 'application.application_id. VENDOR_FEE만',
    CONSTRAINT `PK_PAYMENT`                 PRIMARY KEY (`payment_id`),
    CONSTRAINT `UK_PAYMENT_IDEMPOTENCY_KEY` UNIQUE (`idempotency_key`),
    CONSTRAINT `CK_PAYMENT_AMOUNT`          CHECK (`amount` >= 0),
    KEY `idx_payment_fair_status_created`     (`fair_id`, `status`, `created_at`),
    KEY `idx_payment_business_status_created` (`business_id`, `status`, `created_at`),
    KEY `idx_payment_payer`                   (`payer_user_id`),
    KEY `idx_payment_reservation`             (`reservation_id`),
    KEY `idx_payment_application`             (`application_id`),
    KEY `idx_payment_paid_at`                 (`paid_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `refund` (
    `refund_id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `payment_id`           BIGINT      NOT NULL COMMENT 'payment.payment_id',
    `refund_reason`        VARCHAR(50) NOT NULL
        COMMENT 'USER_CANCEL / FAIR_CANCEL_USER / VENDOR_CANCEL / FAIR_CANCEL_VENDOR / OPENING_FEE_MANUAL',
    `requested_by_domain`  VARCHAR(30) NOT NULL COMMENT '환불을 촉발한 도메인',
    `refund_amount`        BIGINT      NOT NULL COMMENT 'MVP는 전액환불',
    `status`               VARCHAR(20) NOT NULL COMMENT 'REQUESTED / COMPLETED / REJECTED',
    `requested_at`         DATETIME    NOT NULL,
    `processed_at`         DATETIME    NULL,
    `created_at`           DATETIME    NOT NULL,
    `updated_at`           DATETIME    NOT NULL,
    CONSTRAINT `PK_REFUND`         PRIMARY KEY (`refund_id`),
    CONSTRAINT `UK_REFUND_PAYMENT` UNIQUE (`payment_id`),
    KEY `idx_refund_status_requested` (`status`, `requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `settlement` (
    `settlement_id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `fair_id`               BIGINT       NOT NULL COMMENT 'fairs.fair_id',
    `business_id`           BIGINT       NOT NULL COMMENT 'business.business_id',
    `gross_amount`          BIGINT       NOT NULL COMMENT 'COMPLETED VENDOR_FEE 합계',
    `refund_amount`         BIGINT       NOT NULL COMMENT '위 결제에 걸린 환불 합계',
    `commission_rate`       DECIMAL(5,4) NOT NULL COMMENT '정산 시점 요율 스냅샷. 이후 요율이 바뀌어도 과거 정산은 불변',
    `commission_amount`     BIGINT       NOT NULL COMMENT '(gross - refund) x rate',
    `net_amount`            BIGINT       NOT NULL COMMENT '업체 지급액',
    `status`                VARCHAR(20)  NOT NULL COMMENT 'PENDING / CONFIRMED / PAID',
    `paid_at`               DATETIME     NULL,
    `confirmed_at`          DATETIME     NULL,
    `confirmed_by_user_id`  BIGINT       NULL     COMMENT '확정한 SUPER_ADMIN users.user_id',
    `created_at`            DATETIME     NOT NULL,
    `updated_at`            DATETIME     NOT NULL,
    CONSTRAINT `PK_SETTLEMENT`                PRIMARY KEY (`settlement_id`),
    CONSTRAINT `UK_SETTLEMENT_FAIR_BUSINESS`  UNIQUE (`fair_id`, `business_id`),
    KEY `idx_settlement_status_created` (`status`, `created_at`),
    KEY `idx_settlement_business`       (`business_id`),
    KEY `idx_settlement_confirmed_by`   (`confirmed_by_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `settlement_item` (
    `settlement_item_id` BIGINT NOT NULL AUTO_INCREMENT,
    `settlement_id`      BIGINT NOT NULL COMMENT 'settlement.settlement_id',
    `payment_id`         BIGINT NOT NULL COMMENT 'payment.payment_id',
    `refund_id`          BIGINT NULL     COMMENT 'refund.refund_id. 환불이 있을 때만',
    `amount_included`    BIGINT NOT NULL COMMENT '이 건이 합계에 기여한 금액',
    CONSTRAINT `PK_SETTLEMENT_ITEM`         PRIMARY KEY (`settlement_item_id`),
    CONSTRAINT `UK_SETTLEMENT_ITEM_PAYMENT` UNIQUE (`payment_id`),
    KEY `idx_settlement_item_settlement` (`settlement_id`),
    KEY `idx_settlement_item_refund`     (`refund_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `commission_rate` (
    `commission_rate_id`  BIGINT       NOT NULL AUTO_INCREMENT,
    `scope`               VARCHAR(10)  NOT NULL COMMENT 'GLOBAL / FAIR',
    `rate`                DECIMAL(5,4) NOT NULL COMMENT '0.0500 = 5%',
    `fair_id`             BIGINT       NULL     COMMENT 'scope=GLOBAL이면 NULL, scope=FAIR면 fairs.fair_id',
    `updated_by_user_id`  BIGINT       NOT NULL COMMENT 'SUPER_ADMIN users.user_id',
    `updated_at`          DATETIME     NOT NULL,
    CONSTRAINT `PK_COMMISSION_RATE` PRIMARY KEY (`commission_rate_id`),
    CONSTRAINT `CK_COMMISSION_SCOPE` CHECK (
        (`scope` = 'GLOBAL' AND `fair_id` IS NULL) OR
        (`scope` = 'FAIR'   AND `fair_id` IS NOT NULL)
    ),
    KEY `idx_commission_scope_fair_updated` (`scope`, `fair_id`, `updated_at`),
    KEY `idx_commission_updated_by`         (`updated_by_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- (7) 알림 · 감사
-- 이 블록의 두 FK 는 원본 DDL 에 명시된 것이라 그대로 유지한다.
-- users 는 (1) 블록에서 이미 생성되어 있으므로 순서상 문제없다.
-- =========================================================

CREATE TABLE `notification` (
    `notification_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT       NOT NULL COMMENT '수신자 users.user_id',
    `recipient_type`  VARCHAR(20)  NOT NULL COMMENT 'USER / VENDOR / EVENT_ADMIN / SUPER_ADMIN',
    `type`            VARCHAR(50)  NOT NULL COMMENT 'APPLICATION_APPROVED, QR_ISSUED 등',
    `title`           VARCHAR(100) NOT NULL,
    `body`            TEXT         NOT NULL,
    `link_url`        VARCHAR(255) NULL     COMMENT '딥링크. 단순 공지는 NULL',
    `created_at`      DATETIME     NOT NULL,
    CONSTRAINT `PK_NOTIFICATION` PRIMARY KEY (`notification_id`),
    KEY `idx_notification_user_created` (`user_id`, `created_at`),
    KEY `idx_notification_type_created` (`type`, `created_at`),
    CONSTRAINT `FK_NOTIFICATION_USER`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `notification_delivery` (
    `delivery_id`        BIGINT       NOT NULL AUTO_INCREMENT,
    `notification_id`    BIGINT       NOT NULL COMMENT 'notification.notification_id',
    `channel`            VARCHAR(20)  NOT NULL COMMENT 'EMAIL / SMS / IN_APP',
    `status`             VARCHAR(20)  NOT NULL COMMENT 'PENDING / SENT / FAILED',
    `recipient_contact`  VARCHAR(255) NULL     COMMENT 'IN_APP은 NULL',
    `sent_at`            DATETIME     NULL     COMMENT '발송 성공 시각',
    `fail_reason`        VARCHAR(255) NULL,
    `read_at`            DATETIME     NULL     COMMENT 'IN_APP 전용',
    `created_at`         DATETIME     NOT NULL COMMENT '발송 요청 생성 시각',
    CONSTRAINT `PK_NOTIFICATION_DELIVERY` PRIMARY KEY (`delivery_id`),
    KEY `idx_notification_delivery_notification`   (`notification_id`),
    KEY `idx_notification_delivery_status_created` (`status`, `created_at`),
    CONSTRAINT `FK_NOTIFICATION_DELIVERY_NOTIFICATION`
        FOREIGN KEY (`notification_id`) REFERENCES `notification` (`notification_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE `audit_log` (
    `audit_id`      BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT      NOT NULL COMMENT '행위자 users.user_id',
    `actor_role`    VARCHAR(20) NOT NULL COMMENT 'USER / VENDOR / EVENT_ADMIN / SUPER_ADMIN',
    `action_type`   VARCHAR(50) NOT NULL COMMENT 'APPROVE / REJECT / CANCEL / ROLE_CHANGE 등',
    `target_type`   VARCHAR(50) NOT NULL COMMENT 'FAIR / APPLICATION / BOOTH / ACCOUNT / SETTLEMENT 등',
    `target_id`     BIGINT      NOT NULL,
    `before_value`  JSON        NULL COMMENT '신규 생성 액션은 없음',
    `after_value`   JSON        NULL COMMENT '삭제 액션은 없음',
    `occurred_at`   DATETIME    NOT NULL,
    CONSTRAINT `PK_AUDIT_LOG` PRIMARY KEY (`audit_id`),
    KEY `idx_audit_user_occurred`   (`user_id`, `occurred_at`),
    KEY `idx_audit_target_occurred` (`target_type`, `target_id`, `occurred_at`),
    KEY `idx_audit_action_occurred` (`action_type`, `occurred_at`),
    CONSTRAINT `FK_AUDIT_LOG_USER`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- (8) 뷰
-- =========================================================

CREATE VIEW `public_fairs` AS
SELECT `fair_id`,
       `name`,
       `description`,
       `category`,
       `poster_image_url`,
       `notice_text`,
       `place_name`,
       `address`,
       `indoor_outdoor`,
       `reservation_start_date`,
       `reservation_end_date`,
       `operation_start_date`,
       `operation_end_date`,
       `reservation_fee`,
       `reservation_cancel_deadline_hours`,
       `reservation_change_deadline_hours`,
       `status`,
       `published_at`
  FROM `fairs`
 WHERE `status` IN ('PREPARING', 'IN_PROGRESS', 'ENDED')
   AND `canceled_at` IS NULL;
