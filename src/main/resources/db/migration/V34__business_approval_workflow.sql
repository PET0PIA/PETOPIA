-- =========================================================
-- V34__business_approval_workflow.sql
-- 사업자 등록을 자동승인에서 관리자 승인제로 전환.
-- NTS 진위확인(verify_status)은 그대로 두고, 관리자 심사 결과를 위한
-- approval_status를 별도 컬럼으로 신설한다(둘은 별개 개념 - NTS 일치는
-- 필요조건일 뿐 더 이상 충분조건이 아님).
-- =========================================================

ALTER TABLE `business`
    ADD COLUMN `business_reg_doc_key` VARCHAR(500) NULL
        COMMENT '사업자등록증 첨부파일 S3 objectKey. 구버전 자동승인 행은 NULL' AFTER `website`,
    ADD COLUMN `approval_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW'
        COMMENT 'PENDING_REVIEW / APPROVED / REJECTED / REVOKED' AFTER `verify_status`,
    ADD COLUMN `reject_reason` VARCHAR(500) NULL COMMENT '반려/취소 사유' AFTER `approval_status`,
    ADD COLUMN `reviewed_by`   BIGINT   NULL COMMENT '심사한 SUPER_ADMIN users.user_id. NULL=구버전 자동승인' AFTER `reject_reason`,
    ADD COLUMN `reviewed_at`   DATETIME NULL COMMENT '심사 처리 일시' AFTER `reviewed_by`;

-- 기존 행은 전부 자동승인 시절 데이터(verify_status=VERIFIED)이므로 grandfather 처리 -
-- 이미 VENDOR가 된 소유자들을 그대로 유지한다.
UPDATE `business`
SET `approval_status` = 'APPROVED', `reviewed_at` = `created_at`
WHERE `verify_status` = 'VERIFIED';

-- biz_reg_no UNIQUE가 REVOKED된 행도 그대로 잠가버리면 진짜 소유주가 같은 번호로
-- 재신청을 못 하게 되므로, "REVOKED가 아닌 행만" 대상으로 하는 계산 컬럼 +
-- 조건부 유니크로 교체한다.
ALTER TABLE `business` DROP INDEX `UK_BUSINESS_BIZ_REG_NO`;

ALTER TABLE `business`
    ADD COLUMN `active_biz_reg_no` VARCHAR(20) AS (
        CASE WHEN `approval_status` = 'REVOKED' THEN NULL ELSE `biz_reg_no` END
    ) STORED COMMENT 'REVOKED면 NULL - biz_reg_no 재사용(재신청) 허용용 계산 컬럼' AFTER `biz_reg_no`,
    ADD CONSTRAINT `UK_BUSINESS_ACTIVE_BIZ_REG_NO` UNIQUE (`active_biz_reg_no`);

ALTER TABLE `business`
    ADD KEY `idx_business_approval_created` (`approval_status`, `created_at`);