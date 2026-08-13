ALTER TABLE `payment`
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL
        COMMENT 'PENDING / PROCESSING / WAITING_FOR_DEPOSIT / COMPLETED / FAILED / CANCELED / EXPIRED',
    ADD COLUMN `easy_pay_provider` VARCHAR(30) NULL
        COMMENT '간편결제 제공사(NAVER_PAY 등). 카드/가상계좌 등 일반결제는 NULL'
        AFTER `method`,
    ADD COLUMN `virtual_account_bank_code` VARCHAR(10) NULL
        COMMENT '가상계좌 은행코드. VIRTUAL_ACCOUNT 결제가 발급된 뒤에만 값 존재'
        AFTER `easy_pay_provider`,
    ADD COLUMN `virtual_account_number` VARCHAR(50) NULL
        COMMENT '가상계좌 계좌번호'
        AFTER `virtual_account_bank_code`,
    ADD COLUMN `virtual_account_due_date` DATETIME NULL
        COMMENT '가상계좌 입금기한'
        AFTER `virtual_account_number`,
    ADD COLUMN `virtual_account_secret` VARCHAR(50) NULL
        COMMENT '가상계좌 입금통지 웹훅(DEPOSIT_CALLBACK) 검증용 - 토스 confirm 응답의 값과 웹훅 body의 값이 일치해야 정상 웹훅으로 간주'
        AFTER `virtual_account_due_date`;
