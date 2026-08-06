package com.ms.petopia.api.fair.dto;

import java.time.LocalTime;

/**
 * 운영일 수정 요청. operationDate는 수정 대상이 아니다 - fair_dates가
 * (fair_id, operation_date) 단위로 예약 가능일을 정의하는 키라서, 날짜 자체를 바꾸면
 * 이미 그 날짜로 예약한 사람들과 어긋난다. 날짜를 바꾸려면 삭제 후 새로 등록한다.
 *
 * <p>capacity/entryStartTime/entryEndTime은 부분 수정이 아니라 항상 셋 다 같이 받는다 -
 * 입장 시작·종료 시간은 서로를 검증해야 하는 값이라 따로 갱신하게 두지 않는다.
 */
public record UpdateFairDateRequest(
        Integer capacity,
        LocalTime entryStartTime,
        LocalTime entryEndTime
) {
}
