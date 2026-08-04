ALTER TABLE `audit_log`
    DROP FOREIGN KEY `FK_AUDIT_LOG_USER`;

ALTER TABLE `audit_log`
    MODIFY COLUMN `user_id`    BIGINT      NULL
        COMMENT '행위자 users.user_id. 시스템 자동 처리이면 NULL',
    ADD    COLUMN `actor_type` VARCHAR(20) NOT NULL DEFAULT 'ADMIN'
        COMMENT 'USER / ADMIN / SYSTEM / PAYMENT' AFTER `user_id`,
    ADD    CONSTRAINT `CK_AUDIT_LOG_ACTOR_TYPE`
        CHECK (`actor_type` IN ('USER', 'ADMIN', 'SYSTEM', 'PAYMENT'));

ALTER TABLE `audit_log`
    ADD CONSTRAINT `FK_AUDIT_LOG_USER`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`);
