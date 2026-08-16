package com.ms.petopia.api.reservation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 잘못 설정하면 <b>운영 중에 알아채기 어렵다</b> — 결제 화면에 머물던 사용자만 조용히
 * 슬롯을 잃기 때문이다. 그래서 기동 시점에 막는다.
 */
class WaitingRoomPropertiesTest {

    @Test
    @DisplayName("활성 슬롯 수명이 결제 제한시간 이하면 기동을 막는다")
    void activeTtl_결제제한시간이하면_거절한다() {
        assertThatThrownBy(() -> new WaitingRoomProperties(
                ReservationPaymentPolicy.PAYMENT_WAIT, Duration.ofMinutes(30), 1000))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new WaitingRoomProperties(
                Duration.ofMinutes(5), Duration.ofMinutes(30), 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 토큰이 슬롯보다 먼저 사라지면 통과한 사용자가 본인 확인에 실패해 되돌려보내진다. */
    @Test
    @DisplayName("토큰 수명이 활성 슬롯 수명보다 짧으면 기동을 막는다")
    void ticketTtl_활성슬롯보다짧으면_거절한다() {
        assertThatThrownBy(() -> new WaitingRoomProperties(
                Duration.ofMinutes(12), Duration.ofMinutes(11), 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("값을 비워두면 기본값으로 채운다")
    void 기본값을_채운다() {
        WaitingRoomProperties properties = new WaitingRoomProperties(null, null, 0);

        assertThat(properties.activeTtl()).isEqualTo(Duration.ofMinutes(12));
        assertThat(properties.ticketTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.defaultActiveLimit()).isEqualTo(1000);
    }
}
