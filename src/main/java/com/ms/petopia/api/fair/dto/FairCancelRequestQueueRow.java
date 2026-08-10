package com.ms.petopia.api.fair.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 관리자 취소 신청 큐({@code FairCancelRequestQueueController}) 조회 전용 행. fair_cancel_requests와
 * fairs를 조인해서 fairName까지 함께 담는다 - 특정 행사에 갇히지 않고 전체 행사를 가로질러
 * 보여주는 목록이라, fairId만으로는 어느 행사인지 바로 알아보기 어렵다.
 *
 * <p>record가 아닌 이유는 {@link Fair}/{@link FairTransitionRow}와 동일 - MyBatis 세터 기반 매핑.
 */
@Getter
@Setter
public class FairCancelRequestQueueRow {

    private Long fairCancelRequestId;
    private Long fairId;
    private String fairName;
    private Long requestedBy;
    private String reason;
    private FairCancelRequestStatus status;
    private LocalDateTime createdAt;
}
