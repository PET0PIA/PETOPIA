-- =========================================================
-- V11__fair_cancel_requests_pending_unique.sql
-- fair_cancel_requests에 행사당 PENDING 취소 신청 1건 제약 추가
--
-- FairCancelRequestService.create()는 "PENDING 신청이 있는지 조회 후 insert"라
-- 두 요청이 동시에 들어오면 조회 사이 레이스로 같은 행사에 PENDING이 두 건 이상
-- 생길 수 있었다. application.active_key와 동일한 패턴으로, PENDING일 때만
-- fair_id를 값으로 갖는 계산 컬럼에 유니크 제약을 걸어 DB가 최종 방어선이 되게
-- 한다(FairCancelRequestService가 DuplicateKeyException을
-- FAIR_CANCEL_NOT_REQUESTABLE로 변환한다).
-- =========================================================

ALTER TABLE `fair_cancel_requests`
    ADD COLUMN `pending_key` BIGINT AS (
        CASE WHEN `status` = 'PENDING' THEN `fair_id` ELSE NULL END
    ) STORED COMMENT 'PENDING 중복 방지용 계산 컬럼. PENDING 아니면 NULL' AFTER `status`,
    ADD CONSTRAINT `UK_FAIR_CANCEL_REQUESTS_PENDING` UNIQUE (`pending_key`);
