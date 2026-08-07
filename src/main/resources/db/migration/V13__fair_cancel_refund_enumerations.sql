-- =========================================================
-- V13__fair_cancel_refund_enumerations.sql
-- 행사 취소 환불 발견(enumerate) 단계의 완료 여부를 행사 단위로 기록
--
-- fair_cancel_refund_targets(V12)에 행이 있는지로는 "발견 단계를 끝까지 완료했는지"를
-- 판단할 수 없다: 참가비 결제가 0건인 행사는 애초에 작업행이 안 생기고, 결제유형 중
-- 하나만 처리하다 실패해도 이미 등록된 행이 있으면 그 행사는 다시 훑을 대상에서
-- 영구히 빠져버린다. 그래서 완료 여부를 이 테이블에 별도로,
-- 행사 하나가 모든 결제유형·모든 페이지를 예외 없이 다 훑았을 때만 기록한다.
-- =========================================================

CREATE TABLE `fair_cancel_refund_enumerations` (
    `fair_id`       BIGINT   NOT NULL COMMENT 'fairs.fair_id',
    `enumerated_at` DATETIME NOT NULL,
    CONSTRAINT `PK_FAIR_CANCEL_REFUND_ENUMERATIONS` PRIMARY KEY (`fair_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
