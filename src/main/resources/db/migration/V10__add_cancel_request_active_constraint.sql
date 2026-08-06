-- 취소 요청 중복 방지: 신청서 하나당 처리 대기 중인(REQUESTED) 취소 요청이 동시에 2개 이상 존재할 수 없도록 제약 추가
ALTER TABLE application_cancel_request
    ADD COLUMN active_cancel_key VARCHAR(50) AS (
        CASE WHEN status = 'REQUESTED' THEN application_id ELSE NULL END
    ) STORED COMMENT '처리 대기 중인 취소 요청 중복 방지용 계산 컬럼. REQUESTED 아니면 NULL',
    ADD CONSTRAINT UK_APPLICATION_CANCEL_ACTIVE UNIQUE (active_cancel_key);