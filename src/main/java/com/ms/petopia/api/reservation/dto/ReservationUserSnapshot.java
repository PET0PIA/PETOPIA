package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 예약 시점에 예약자 정보로 복사할 회원 정보.
 */
@Getter
@Setter
@ToString
public class ReservationUserSnapshot {

    private Long userId;
    private String nickname;
    private String phone;
    private String email;
    private String role;
    private String status;
}
