ALTER TABLE `payment`
    ADD COLUMN `toss_payment_key` VARCHAR(200) NULL COMMENT '토스 결제승인 완료 후 발급되는 결제키, 환불 시 필요'
    AFTER `idempotency_key`;