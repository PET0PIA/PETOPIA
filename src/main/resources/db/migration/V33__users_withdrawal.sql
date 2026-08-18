ALTER TABLE `users`
    ADD COLUMN `deleted_at`      DATETIME     NULL COMMENT '탈퇴 처리 일시' AFTER `updated_at`,
    ADD COLUMN `withdrawn_email` VARCHAR(255) NULL COMMENT '탈퇴 시점 원래 이메일 (15일 재가입 제한 조회용)' AFTER `deleted_at`,
    ADD KEY `idx_users_withdrawn_email` (`withdrawn_email`, `deleted_at`);
