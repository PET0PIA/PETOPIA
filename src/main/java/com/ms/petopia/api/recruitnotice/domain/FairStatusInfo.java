package com.ms.petopia.api.recruitnotice.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// fairs 테이블에서 모집 공고 마감(closed) 판정에만 필요한 최소한의 정보를 담는 참조용 클래스.
@Getter
@Setter
public class FairStatusInfo {

    private LocalDateTime canceledAt; // NULL이면 취소 아님(fairs.canceled_at)
    private String status; // 행사 상태값. "ENDED"면 마감 판정에 포함(fairs.status)

}
