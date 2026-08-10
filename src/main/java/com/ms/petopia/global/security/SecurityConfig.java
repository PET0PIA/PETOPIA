package com.ms.petopia.global.security;


import com.ms.petopia.global.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    //특정 HTTP 요청에 대한 웹 기반 보안 구성
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws  Exception {
        httpSecurity
                //csrf는 사용을 하지 않기 때문에 disable 처리
                //JWT 기반 무상태 API이므로 세션을 만들지 않는다. 이 전제에서만 csrf disable이 안전하다.
                .csrf(csrf -> csrf.disable())
                //Security 필터 체인이 CORS를 인지하게 함
                //JwtAuthenticationFilter를 타면서 엉뚱하게 처리되는 걸 막는다.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/reservations/**",
                                "/api/v1/fairs/*/reservations",
                                "/api/v1/fairs/*/onsite-reservations"
                        ).authenticated()
                        .requestMatchers("/api/v1/admin/fairs/**")
                        .hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        // 시스템 전체를 가로지르는 관리자 API(감사 로그, 전체 대시보드) - SUPER_ADMIN 전용.
                        // AuditLogController, AdminDashboardController가 여기 해당한다.
                        .requestMatchers("/api/admin/**")
                        .hasRole("SUPER_ADMIN")
                        //참가업체 부스 운영 API(부스 방문 스캔 등). 부스 소유 검증은 서비스 계층에서 한 번 더 한다.
                        .requestMatchers("/api/v1/vendor/**")
                        .hasRole("VENDOR")
                        //로그인한 본인만 비밀번호 변경 가능 - anyRequest().permitAll()보다 먼저 와야 함
                        .requestMatchers(HttpMethod.PATCH, "/api/auth/password/change").authenticated()
                        //TODO 추후 role 기반 가드 확장
                        .requestMatchers(HttpMethod.POST, "/api/files/presigned-upload").authenticated()
                        //user 권한을 필요
                        //.requestMatchers("/user").hasAuthority("ROLE_USER")
                        //TODO 추후 구현
                        // Fair 도메인 - 신청자 API(로그인만 필요, 본인 신청 여부는 서비스 계층에서 검증)
                        .requestMatchers(HttpMethod.POST, "/api/fairs").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/fairs/mine", "/api/fairs/*/mine").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/fairs/*").authenticated()
                        // Fair 도메인 - 공개된 행사 요약 조회는 인증 없이 허용(티켓 예매 화면 등).
                        // "/api/fairs/*"(SUPER_ADMIN 전용, 아래)와 세그먼트 수가 달라 원래도 안 겹치지만
                        // (Ant *는 세그먼트 하나만 매치), 의도를 명시하려고 따로 적어둔다.
                        .requestMatchers(HttpMethod.GET, "/api/fairs/*/public").permitAll()
                        // Fair 도메인 - 공개 행사 목록(지난/예정 행사) 조회도 인증 없이 허용.
                        // "/api/fairs/*"(SUPER_ADMIN 전용, 바로 아래)와 세그먼트 패턴이 겹쳐서
                        // ("/api/fairs/public"도 "/api/fairs/*"에 매치됨) 반드시 그 규칙보다 먼저 와야 한다.
                        .requestMatchers(HttpMethod.GET, "/api/fairs/public").permitAll()
                        // Fair 도메인 - SUPER_ADMIN 전용(신청서 심사 큐/상세 조회, 심사, 공개, 취소 신청 검토).
                        // "/api/fairs"(세그먼트 없음)는 "/api/fairs/*"에 안 걸려서 따로 적어야 한다.
                        .requestMatchers(HttpMethod.GET, "/api/fairs").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/fairs/*").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/fairs/*/review", "/api/fairs/*/publish").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/fairs/*/fair-cancel-requests/*/review").hasRole("SUPER_ADMIN")
                        // Fair 도메인 - 취소 신청 큐(전체 행사를 가로질러 조회, fairId 없이 접근).
                        // "/api/fairs/*/fair-cancel-requests"와 경로 자체가 다르므로(prefix가
                        // "/api/fairs"가 아니라 "/api/fair-cancel-requests") 서로 안 겹친다.
                        .requestMatchers(HttpMethod.GET, "/api/fair-cancel-requests").hasRole("SUPER_ADMIN")
                        // Fair 도메인 - 행사 관리자(EVENT_ADMIN)가 자기 행사의 취소를 신청/이력 조회.
                        // role만으로는 "그 행사 담당자인지"까지 못 가린다(다른 행사 EVENT_ADMIN이
                        // 남의 행사 취소를 신청하거나 이력을 볼 수 있음) - FairAdminAccessGuard가
                        // 서비스 계층에서 한 번 더 확인한다.
                        .requestMatchers(HttpMethod.POST, "/api/fairs/*/fair-cancel-requests").hasRole("EVENT_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/fairs/*/fair-cancel-requests").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        // Fair 도메인 - 홀/부스 슬롯/운영일 관리(그 행사 담당 EVENT_ADMIN 또는 SUPER_ADMIN).
                        // "/api/fairs/*/halls/**"가 BoothSlotController 경로(.../halls/{hallId}/booth-slots)도
                        // 함께 덮는다. 담당 fair인지(소유자 검증)는 FairAdminAccessGuard가 서비스 계층에서
                        // 한 번 더 확인한다.
                        .requestMatchers("/api/fairs/*/halls/**").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/fairs/*/fair-dates/**").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        // Statistics 도메인 - 행사 하나에 대한 예약/방문 통계 대시보드(ReservationDashboardController).
                        // halls/fair-dates와 같은 이유로 그 행사 담당 EVENT_ADMIN 또는 SUPER_ADMIN만 접근.
                        .requestMatchers(HttpMethod.GET,
                                "/api/fairs/*/reservation-dashboard",
                                "/api/fairs/*/reservation-dashboard/stream",
                                "/api/fairs/*/qr-issuance-summary",
                                "/api/fairs/*/hourly-entry-trend",
                                "/api/fairs/*/booth-visit-stats",
                                "/api/fairs/*/booth-visit-pattern",
                                "/api/fairs/*/visit-stats",
                                "/api/fairs/*/visit-stats/export"
                        ).hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        // FairPaymentContractController(/internal/api/v1/**)는 사용자 JWT가 아니라
                        // 도메인 간 내부 호출자 헤더(X-Internal-Caller)로 별도 인증하므로 여기서 다루지 않는다.
                        .anyRequest().permitAll())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, e) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return httpSecurity.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JwtAuthenticationFilter는 SecurityFilterChain에서만 실행한다.
     * 컴포넌트 스캔에 의한 서블릿 필터 자동 등록까지 허용하면 인증 규칙보다 늦게 실행될 수 있다.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
