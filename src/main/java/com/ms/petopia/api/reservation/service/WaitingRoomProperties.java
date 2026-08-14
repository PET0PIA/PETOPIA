package com.ms.petopia.api.reservation.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 대기열 중 <b>행사별로 다를 이유가 없는</b> 값들.
 *
 * <p>대기열을 켤지, 몇 명을 통과시킬지는 운영 중에 바뀌므로 DB
 * ({@code waiting_room_policies})에 있다. 여기 남은 것은 두 가지 성격뿐이다.
 * <ul>
 *   <li>TTL — {@link ReservationPaymentPolicy#PAYMENT_WAIT}와 맞물려 있다. 행사마다 다르게
 *       둘 이유가 없고, 잘못 줄이면 결제 중인 사용자가 슬롯을 잃는다(기동 시 검증한다).
 *       관리자 화면에 노출하지 않는다.</li>
 *   <li>기본 통과 인원 — 아직 정책을 설정한 적 없는 행사의 관리자 화면 초기값.</li>
 * </ul>
 *
 * @param activeTtl          활성 슬롯 유지 시간. {@link ReservationPaymentPolicy#PAYMENT_WAIT}보다
 *                           길어야 예약부터 결제 완료까지 슬롯 하나로 끝난다
 * @param ticketTtl          대기 토큰 자체의 수명. 줄을 선 채 방치된 토큰을 정리한다
 * @param defaultActiveLimit 정책을 아직 만들지 않은 행사에 제안할 통과 인원 초기값
 */
@ConfigurationProperties("petopia.waiting-room")
public record WaitingRoomProperties(
        Duration activeTtl,
        Duration ticketTtl,
        int defaultActiveLimit
) {

    public WaitingRoomProperties {
        if (activeTtl == null) {
            activeTtl = Duration.ofMinutes(12);
        }
        if (ticketTtl == null) {
            ticketTtl = Duration.ofMinutes(30);
        }
        if (defaultActiveLimit <= 0) {
            defaultActiveLimit = 1000;
        }
        // 결제 제한시간보다 짧으면 아직 결제할 수 있는 사용자가 슬롯을 먼저 잃는다.
        // 운영 중에 알아채기 어려운 종류의 사고라 기동 자체를 막는다.
        if (activeTtl.compareTo(ReservationPaymentPolicy.PAYMENT_WAIT) <= 0) {
            throw new IllegalArgumentException(
                    "petopia.waiting-room.active-ttl은 결제 제한시간("
                            + ReservationPaymentPolicy.PAYMENT_WAIT + ")보다 길어야 합니다. 현재 값=" + activeTtl);
        }
        if (ticketTtl.compareTo(activeTtl) < 0) {
            throw new IllegalArgumentException(
                    "petopia.waiting-room.ticket-ttl은 active-ttl(" + activeTtl
                            + ") 이상이어야 합니다. 현재 값=" + ticketTtl);
        }
    }
}
