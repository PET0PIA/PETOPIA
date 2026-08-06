package com.ms.petopia.api.fair.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code FairTransitionMapper}의 대상 조회 쿼리(select ... for update) 결과 행.
 * reservation 도메인의 {@code ExpiringReservationRow}와 동일한 이유로 fairId 하나만 담는다 -
 * 배치 처리에서 잠근 PK만 넘기고 실제 갱신은 서비스가 조건부 UPDATE로 따로 한다.
 */
@Getter
@Setter
public class FairTransitionRow {
    private Long fairId;
}
