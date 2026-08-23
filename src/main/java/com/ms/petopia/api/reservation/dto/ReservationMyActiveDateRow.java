package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 예매 화면에서 "이 날짜는 내가 이미 잡아둔 날"인지 표시하기 위한 조회 결과 한 줄.
 *
 * <p>예매 화면은 예전에 이 정보를 몰라서, 이미 예약한 날짜도 예약 가능한 것처럼 보여주고
 * 결제 버튼을 누른 뒤에야 R005(중복 예약)로 막았다. 날짜 카드에 미리 표시해 주려고 추가했다.
 * 중복 판정 기준({@code ReservationMapper.existsActiveReservation})과 같은 상태 집합을 본다 -
 * 한쪽만 바뀌면 "예약 가능해 보이는데 눌러보면 막히는" 화면이 다시 생긴다.
 */
@Getter
@Setter
public class ReservationMyActiveDateRow {
    private Long reservationId;
    private LocalDate visitDate;
    private String status;
}
