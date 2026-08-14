package com.ms.petopia.global.web;

import com.ms.petopia.api.reservation.service.WaitingRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 대기열 게이트를 걸 경로를 등록한다.
 *
 * <p>보호 대상은 <b>오픈 순간 몰리는 쓰기 경로만</b>이다. 조회는 대상이 아니다 — 대기 중인
 * 사용자도 잔여 좌석은 볼 수 있어야 하고, 조회를 막으면 사용자가 새로고침을 반복해서
 * 오히려 부하가 는다.
 *
 * <p>현장예매({@code onsite-reservations})는 제외한다. 당일 현장에서 발생하는 트래픽이라
 * 오픈 러시와 성격이 다르고, 현장에서 대기 토큰을 발급받게 하는 것은 동선에 맞지 않는다.
 *
 * <h2>왜 인터셉터를 여기서 직접 만드는가</h2>
 * {@code WebMvcConfigurer}는 {@code @WebMvcTest} 슬라이스가 전부 끌어온다. 인터셉터를
 * {@code @Component}로 두고 생성자로 주입받으면, 예약과 아무 상관 없는 컨트롤러 슬라이스
 * 테스트까지 {@link WaitingRoomService} 빈을 요구하게 된다. 게이트 하나 추가했다고 무관한
 * 테스트가 줄줄이 깨지는 결합은 두지 않는다.
 *
 * <p>그래서 {@link ObjectProvider}로 받아 <b>있으면 걸고 없으면 건너뛴다.</b> 서비스가 없는
 * 컨텍스트는 웹 계층만 띄운 슬라이스뿐이라 게이트가 필요하지도 않다. 다만 실제 애플리케이션에서
 * 이 빈이 사라지면 게이트가 조용히 풀리는 셈이므로, 건너뛸 때는 반드시 경고를 남긴다.
 *
 * <h2>결제 API를 게이트에 넣지 않은 이유</h2>
 * 원래 설계(docs/reservation-payment-scalability-plan.md Phase 7-4)는 결제 준비·승인까지
 * 보호 대상으로 잡았다. 지금 코드 상태에서는 두 가지가 걸린다.
 * <ol>
 *   <li>{@code PaymentController}는 아직 JWT가 아니라 임시 헤더({@code X-User-Id})로
 *       사용자를 받는다. SecurityContext에 인증 주체가 없어 토큰↔사용자 대조 자체가
 *       불가능하고, 게이트를 걸어도 조용히 통과할 뿐이다.</li>
 *   <li>{@code POST /api/payments/*&#47;confirm}은 예약금 전용이 아니다. 참가비·행사개설비
 *       승인도 같은 경로를 쓴다. 여기에 대기 토큰을 요구하면 대기열을 켠 동안 참가업체
 *       결제가 통째로 막힌다.</li>
 * </ol>
 * 그래서 지금은 예약 생성만 막는다. 결제까지 도달하려면 반드시 예약 생성을 먼저 통과해야
 * 하므로 결제 유량도 {@code activeLimit} 안에서 간접적으로 제한된다. 결제 도메인이 JWT로
 * 전환되고 승인 경로가 결제유형별로 갈리면, 그때 예약금 경로만 골라 여기에 추가한다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<WaitingRoomService> waitingRoomServiceProvider;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        WaitingRoomService waitingRoomService = waitingRoomServiceProvider.getIfAvailable();
        if (waitingRoomService == null) {
            log.warn("WaitingRoomService 빈이 없어 대기열 게이트를 등록하지 않는다. "
                    + "웹 계층만 띄운 슬라이스 테스트가 아니라면 예약 API가 무방비 상태다.");
            return;
        }

        registry.addInterceptor(new WaitingRoomInterceptor(waitingRoomService))
                // 예약 생성. 경로에 fairId가 있어 그 행사의 슬롯을 정확히 대조한다.
                .addPathPatterns("/api/v1/fairs/*/reservations");
    }
}
