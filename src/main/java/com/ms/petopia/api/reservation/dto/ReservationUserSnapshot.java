package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 예약 시점에 예약자 정보로 복사할 회원 정보.
 *
 * <p>phone·email은 개인정보라 toString()에서 제외한다. 이 객체가 로그나 예외 메시지에
 * 섞여 들어가도 연락처가 그대로 남지 않도록 하기 위한 것이다.
 */
@Getter
@Setter
@ToString
public class ReservationUserSnapshot {

    private Long userId;
    private String nickname;
    @ToString.Exclude
    private String phone;
    @ToString.Exclude
    private String email;
    private String role;
    private String status;
}
