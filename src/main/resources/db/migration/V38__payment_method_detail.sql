-- 결제수단 상세(간편결제 제공사/가상계좌) 저장 컬럼 추가.
--
-- 지금까지는 payment.method에 토스가 내려주는 대분류("카드"/"가상계좌" 등)만 저장했는데,
-- 두 가지가 더 필요해졌다:
--   1) 간편결제(네이버페이 등)로 결제했을 때 그 "제공사"가 어디였는지 별도로 구분해야 함
--      (토스 confirm 응답의 easyPay.provider)
--   2) 가상계좌는 confirm 응답 시점엔 아직 입금 전(WAITING_FOR_DEPOSIT)이라 계좌정보를
--      화면에 보여줘야 하고, 이후 입금 완료를 웹훅으로 받을 때 그 웹훅이 진짜 토스가
--      보낸 게 맞는지 대조할 secret도 같이 보관해야 함
ALTER TABLE `payment`
    ADD COLUMN `easy_pay_provider` VARCHAR(50) NULL
        COMMENT '간편결제 제공사(토스 easyPay.provider) - 일반 카드/계좌이체 등이면 NULL' AFTER `method`,
    ADD COLUMN `virtual_account_bank_code` VARCHAR(10) NULL
        COMMENT '가상계좌 은행 코드(토스 virtualAccount.bankCode)' AFTER `easy_pay_provider`,
    ADD COLUMN `virtual_account_number` VARCHAR(50) NULL
        COMMENT '가상계좌 계좌번호(토스 virtualAccount.accountNumber)' AFTER `virtual_account_bank_code`,
    ADD COLUMN `virtual_account_due_date` DATETIME NULL
        COMMENT '가상계좌 입금기한(토스 virtualAccount.dueDate)' AFTER `virtual_account_number`,
    ADD COLUMN `virtual_account_secret` VARCHAR(200) NULL
        COMMENT '가상계좌 입금 웹훅 검증용 secret(토스 발급값) - 응답에 그대로 노출하면 안 됨' AFTER `virtual_account_due_date`;
