-- =========================================================
-- V37__business_rejected_reg_no_reusable.sql
-- REJECTED(승인 전 반려)도 REVOKED처럼 biz_reg_no 재사용을 허용한다.
-- V34에서는 REVOKED만 예외 처리했는데, 반려도 "고쳐서 다시 신청"이
-- 흔한 시나리오라 빠뜨리면 진짜 소유주가 영구히 등록 못 하게 된다.
-- =========================================================

ALTER TABLE `business`
    MODIFY COLUMN `active_biz_reg_no` VARCHAR(20) AS (
        CASE WHEN `approval_status` IN ('REVOKED', 'REJECTED') THEN NULL ELSE `biz_reg_no` END
    ) STORED COMMENT 'REVOKED/REJECTED면 NULL - biz_reg_no 재사용(재신청) 허용용 계산 컬럼';