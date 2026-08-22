package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 방문 날짜 변경 요청.
 *
 * @param petIds 동반 반려동물 목록의 <b>교체</b> 값. null이면 기존 동반 정보를 그대로 둔다.
 *               빈 목록을 보내면 동반을 해제한다. 값이 있으면 그 목록으로 스냅샷을 다시 뜨므로,
 *               그 사이 원본 반려동물 정보가 바뀌었다면 변경 시점의 값이 저장된다.
 */
public record UpdateReservationVisitDateRequest(
        LocalDate visitDate,
        List<Long> petIds
) {
    /** 반려동물 수정이 없던 시절 호출부 호환용. 기존 동반 정보를 건드리지 않는다(null). */
    public UpdateReservationVisitDateRequest(LocalDate visitDate) {
        this(visitDate, null);
    }
}
