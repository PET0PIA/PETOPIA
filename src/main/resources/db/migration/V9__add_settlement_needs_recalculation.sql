ALTER TABLE `settlement`
    ADD COLUMN `needs_recalculation` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT 'PENDING 정산에 포함된 결제가 그 뒤 환불되어 재계산이 필요한 상태(CodeRabbit 리뷰 지적, PR #54) - confirm()이 이 값이 서 있으면 확정을 거부한다'
        AFTER `status`;
