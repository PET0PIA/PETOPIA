package com.ms.petopia.api.reservation.service;

/**
 * 예약 취소·변경 마감의 기본값.
 *
 * <p>마감 시각은 행사가 정한다({@code fairs.reservation_cancel_deadline_hours} ·
 * {@code reservation_change_deadline_hours}, "입장 몇 시간 전까지"). 그 값이 비어 있을 때만
 * 여기 기본값을 쓴다.
 *
 * <p>한 곳에 모아둔 이유: 이 값을 서비스마다 따로 적어두면 한쪽만 바뀌었을 때 조용히 어긋난다.
 * 실제로 방문일 변경은 행사 설정을 읽지 않고 12를 고정하고 있어서, 행사가 입력한 변경 기한이
 * 무시되고 있었다. 취소·변경·상세 조회가 모두 이 상수를 참조한다.
 */
public final class ReservationDeadlinePolicy {

    /** 행사가 취소 가능 기한을 정하지 않았을 때 쓰는 기본값(입장 12시간 전까지 취소 가능). */
    public static final int DEFAULT_CANCEL_DEADLINE_HOURS = 12;

    /** 행사가 변경 가능 기한을 정하지 않았을 때 쓰는 기본값(입장 12시간 전까지 변경 가능). */
    public static final int DEFAULT_CHANGE_DEADLINE_HOURS = 12;

    private ReservationDeadlinePolicy() {
    }
}
