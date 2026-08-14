package com.ms.petopia.api.reservation.service;

import java.time.Duration;

/**
 * 예약 결제 제한시간. 예약 생성(사전/현장)과 대기열 슬롯 수명이 함께 참조한다.
 *
 * <p>한 곳에 모아둔 이유는 값이 서로 맞물려 있기 때문이다. 대기열 슬롯이 이 시간보다 먼저
 * 끝나면, 아직 결제할 수 있는 사용자가 슬롯을 잃어 결제를 마치지 못한다.
 * {@link WaitingRoomProperties}가 기동 시점에 그 관계를 검증한다.
 */
public final class ReservationPaymentPolicy {

    /** 예약 생성 후 결제를 마쳐야 하는 시간. */
    public static final Duration PAYMENT_WAIT = Duration.ofMinutes(10);

    public static final long PAYMENT_WAIT_MINUTES = PAYMENT_WAIT.toMinutes();

    private ReservationPaymentPolicy() {
    }
}
