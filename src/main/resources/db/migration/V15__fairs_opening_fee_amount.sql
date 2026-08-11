-- =========================================================
-- V15__fairs_opening_fee_amount.sql
-- fairs에 개설비 금액 컬럼 추가
--
-- 지금까지는 개설비 결제 생성 API(POST /api/fairs/{id}/opening-payment)가 요청 바디의
-- amount를 그대로 신뢰해서 결제를 만들었다. 관리자 승인 화면을 승인 모달(금액 입력)로
-- 개편하면서, 그때 확정한 금액을 fairs 테이블에 저장해두고 이후 결제 생성 API가 이
-- 저장된 금액을 쓰도록 바꾼다(요청 바디 금액을 더 이상 신뢰하지 않음).
--
-- 승인 전(RECEIVED) 신청서와 반려된 신청서는 금액이 없으므로 NULL을 허용한다.
-- =========================================================

ALTER TABLE `fairs`
    ADD COLUMN `opening_fee_amount` BIGINT NULL COMMENT '승인 시 관리자가 정한 개설비 금액(원). 승인 전에는 NULL' AFTER `payment_due_at`;
