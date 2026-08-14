package com.ms.petopia.api.reservation.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 대기열 중 <b>행사별로 다를 이유가 없는</b> 값들.
 *
 * <p>대기열을 켤지, 몇 명을 통과시킬지는 운영 중에 바뀌므로 DB
 * ({@code waiting_room_policies})에 있다. 여기 남은 것은 두 가지 성격뿐이다.
 * <ul>
 *   <li>TTL — 결제 제한시간(10분)과 맞물려 있다. 행사마다 다르게 둘 이유가 없고,
 *       잘못 줄이면 결제 중인 사용자가 슬롯을 잃는다. 관리자 화면에 노출하지 않는다.</li>
 *   <li>기본 통과 인원 — 아직 정책을 설정한 적 없는 행사의 관리자 화면 초기값.</li>
 * </ul>
 *
 * @param activeTtl          활성 슬롯 유지 시간. 결제 대기 시간(10분)보다 길어야 예약부터
 *                           결제 완료까지 슬롯 하나로 끝난다
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
    }
}
