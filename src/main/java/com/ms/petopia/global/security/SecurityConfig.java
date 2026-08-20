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
                                "/api/v1/fairs/*/onsite-reservations",
                                // 대기열 - 토큰을 발급받은 본인인지 대조해야 하므로 로그인이 필요하다.
                                // 미인증으로 통과시키면 토큰 하나로 여러 계정이 게이트를 넘을 수 있다.
                                "/api/v1/fairs/*/waiting-room/**"
                        ).authenticated()
                        .requestMatchers("/api/v1/admin/fairs/**")
                        .hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        // 관리자 로그인 자체는 인증 전 상태에서 호출돼야 하므로 아래 /api/admin/** 규칙보다
                        // 먼저 permitAll로 열어둔다. 비밀번호/역할 검증은 AdminAccountService.adminLogin()이 담당.
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/login").permitAll()
                        // 시스템 전체를 가로지르는 관리자 API(감사 로그, 전체 대시보드) - SUPER_ADMIN 전용.
                        // AuditLogController, AdminDashboardController가 여기 해당한다.
                        //
                        // 상담 콘솔(AdminChatController, /api/admin/chat/**)도 이 규칙에 걸려 SUPER_ADMIN
                        // 전용이다. 박람회 관리자에게도 상담 답변을 열어주려면 그 규칙을 이 줄 "위에"
                        // 놓아야 한다 - 아래에 두면 이 매처가 먼저 잡아 도달하지 못한다.
                        // (박람회 관리자 허용 여부는 미정. 열어줄 때 chat_conversation.fair_id로
                        //  "자기 행사 문의만" 스코프를 함께 걸어야 한다.)
                        .requestMatchers("/api/admin/**")
                        .hasRole("SUPER_ADMIN")
                        //참가업체 부스 운영 API(부스 방문 스캔 등). 부스 소유 검증은 서비스 계층에서 한 번 더 한다.
                        .requestMatchers("/api/v1/vendor/**")
                        .hasRole("VENDOR")
                        // Notification 도메인 - JWT로 전환됨(@AuthenticationPrincipal). 미인증 요청이
                        // permitAll로 통과하면 userId가 null이 되어 조회/처리가 깨지므로 로그인만 요구한다.
                        // POST(다른 도메인 이벤트로 알림을 생성)는 사용자 인증 대상이 아니라 여기서 제외한다.
                        .requestMatchers(HttpMethod.GET, "/api/notifications", "/api/notifications/unread-count")
                        .authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/notifications/*/read", "/api/notifications/read-all")
                        .authenticated()
                        //로그인한 본인만 비밀번호 변경 가능 - anyRequest().permitAll()보다 먼저 와야 함
                        .requestMatchers(HttpMethod.PATCH, "/api/auth/password/change").authenticated()
                        //로그인한 본인만 내 프로필 조회/수정 가능
                        .requestMatchers("/api/users/me").authenticated()
                        //로그인한 본인만 반려동물 등록/조회/수정/삭제 가능
                        .requestMatchers("/api/users/me/pets/**").authenticated()
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
                        // Fair 도메인 - 부스 모집중인 행사 목록도 인증 없이 허용(전체공개 여부와
                        // 무관하게 참가업체가 찾아 신청할 수 있어야 한다). "/api/fairs/*"(SUPER_ADMIN
                        // 전용, 바로 아래)와 패턴이 겹치므로 먼저 와야 한다.
                        .requestMatchers(HttpMethod.GET, "/api/fairs/recruiting").permitAll()
                        // Fair 도메인 - EVENT_ADMIN 전용: 자신에게 배정된 행사 목록.
                        // "/api/fairs/*"(SUPER_ADMIN 전용, 바로 아래)와 패턴이 겹치므로 먼저 와야 한다.
                        .requestMatchers(HttpMethod.GET, "/api/fairs/mine-assigned").hasRole("EVENT_ADMIN")
                        // Fair 도메인 - SUPER_ADMIN 전용(신청서 심사 큐/상세 조회, 심사, 취소 신청 검토).
                        // "/api/fairs"(세그먼트 없음)는 "/api/fairs/*"에 안 걸려서 따로 적어야 한다.
                        .requestMatchers(HttpMethod.GET, "/api/fairs").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/fairs/*").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/fairs/*/review").hasRole("SUPER_ADMIN")
                        // Fair 도메인 - 공개(publish)는 그 행사 담당 EVENT_ADMIN도 할 수 있다. 담당
                        // fair인지(소유자 검증)는 FairAdminAccessGuard가 서비스 계층에서 한 번 더 확인한다.
                        .requestMatchers(HttpMethod.PATCH, "/api/fairs/*/publish").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
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
                        // Fair 도메인 - 개설비 결제 페이지 전용 요약 조회. 담당 EVENT_ADMIN인지는
                        // FairAdminAccessGuard가 서비스 계층에서 한 번 더 확인한다.
                        .requestMatchers(HttpMethod.GET, "/api/fairs/*/opening-fee").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        // Review 도메인(V39 재설계 - 태그 기반 통합 리뷰) - 로그인만 하면 호출은
                        // 가능하지만, 실제 제출은 해당 행사 방문 이력(entry_records)이 있어야
                        // 하고 행사당 1건 제한이다 - FairReviewService가 서비스 계층에서 검증한다.
                        .requestMatchers(HttpMethod.POST, "/api/fairs/*/reviews").authenticated()
                        // Review 도메인 - "이미 작성했는지" 상태 조회는 로그인한 본인 것만.
                        .requestMatchers(HttpMethod.GET, "/api/fairs/*/reviews/status").authenticated()
                        // Review 도메인 - 행사관리자 통계(카테고리별 태그 TOP5 등)는 그 행사 담당
                        // EVENT_ADMIN 또는 SUPER_ADMIN만 - FairAdminAccessGuard가 서비스 계층에서 확인한다.
                        .requestMatchers(HttpMethod.GET, "/api/fairs/*/reviews/stats").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        // FeedbackTag 도메인 - 태그 마스터 활성 목록 조회는 누구나(리뷰 마법사에서 사용).
                        // 등록·수정·사용현황 조회는 SUPER_ADMIN만.
                        .requestMatchers(HttpMethod.GET, "/api/feedback-tags").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/feedback-tags").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/feedback-tags/*").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/feedback-tags/*/usage").hasRole("SUPER_ADMIN")
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
                        // Payment 도메인 - 결제 생성/확정/내 결제내역은 로그인만 필요(소유자 검증은
                        // 서비스 계층). 결제 단건 조회(GET /api/payments/{id})는 소유자 또는 그 행사
                        // 담당 EVENT_ADMIN/SUPER_ADMIN만 - PaymentService.getPayment(id, userId)가 확인한다.
                        .requestMatchers(HttpMethod.GET, "/api/payments/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/reservations/*/payment").authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api/vendor-applications/*/payment",
                                "/api/payments/*/confirm",
                                "/api/reservations/*/payment",
                                "/api/fairs/*/opening-payment"
                        ).authenticated()
                        // Payment 도메인 - 조건별 결제 목록(관리자용). fairId 없이 전체를 보는 건
                        // SUPER_ADMIN만, fairId를 주면 그 행사 담당 EVENT_ADMIN도 가능 - 세부 스코핑은
                        // PaymentController.getPayments가 FairAdminAccessGuard로 한 번 더 확인한다.
                        .requestMatchers(HttpMethod.GET, "/api/payments").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/me/payments").authenticated()
                        // Refund 도메인 - 환불 요청은 결제 소유자 또는 그 행사 담당 관리자만
                        // (RefundService.assertRequesterAuthorized가 확인). 조회는 로그인만 요구.
                        .requestMatchers(HttpMethod.POST, "/api/payments/*/refunds").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/payments/*/refunds", "/api/refunds/*").authenticated()
                        // Settlement 도메인 - 계산/확정/재계산/조회/엑셀 export 전부 그 행사 담당
                        // EVENT_ADMIN 또는 SUPER_ADMIN만(금액이 포함된 관리자 전용 기능이라 개별 조회보다
                        // 접근을 좁힘). 행사 담당자인지는 SettlementService가 FairAdminAccessGuard로
                        // 한 번 더 확인한다(계산/확정/재계산은 fairId를, confirm/recalculate는 정산
                        // 행에서 읽은 fairId를 기준으로).
                        .requestMatchers(HttpMethod.POST, "/api/fairs/*/vendors/*/settlements").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/settlements/*/confirm", "/api/settlements/*/recalculate").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        // reopen(확정취소)은 confirm/recalculate보다 더 신중해야 하는 결정이라
                        // EVENT_ADMIN은 빼고 SUPER_ADMIN만 허용한다(서비스 계층 requireSuperAdmin과 이중 방어).
                        .requestMatchers(HttpMethod.PUT, "/api/settlements/*/reopen").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/fairs/*/vendors/*/settlement", "/api/fairs/*/settlements").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/fairs/*/settlements/export").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        // CommissionRate 도메인 - 현재 요율 조회는 결제 화면 등에서 누구나 볼 수 있게
                        // permitAll, 설정 변경만 SUPER_ADMIN.
                        .requestMatchers(HttpMethod.GET, "/api/settlements/commission-rate").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/settlements/commission-rate").hasRole("SUPER_ADMIN")
                        // PaymentWebhookController - 토스가 사용자 JWT 없이 직접 호출하는 가상계좌
                        // 입금 웹훅. 위조 방지는 여기(인증)가 아니라 PaymentService.handleDepositWebhook의
                        // secret 대조가 담당한다.
                        .requestMatchers(HttpMethod.POST, "/webhooks/toss/**").permitAll()
                        // FairPaymentContractController(/internal/api/v1/**)는 사용자 JWT가 아니라
                        // 도메인 간 내부 호출자 헤더(X-Internal-Caller)로 별도 인증하므로 여기서 다루지 않는다.
                        // Business 도메인 - 등록/조회는 로그인만 필요. VENDOR 승격은 등록 시점이 아니라
                        // 관리자 승인(PATCH /api/businesses/*/approve) 시점에 서비스 계층에서 처리한다.
                        // 심사 조회·승인·반려·취소는 SUPER_ADMIN 전용이며, 아래 "/api/businesses/*"
                        // (authenticated) 규칙보다 반드시 먼저 와야 한다.
                        .requestMatchers(HttpMethod.GET, "/api/businesses/review").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/businesses/*/review").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/businesses/*/approve").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/businesses/*/reject").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/businesses/*/revoke").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/businesses", "/api/businesses/*").authenticated()
                        // RecruitNotice 도메인 - 그 행사 담당 EVENT_ADMIN 또는 SUPER_ADMIN. 담당 fair인지는
                        // FairAdminAccessGuard가 서비스 계층에서 한 번 더 확인한다.
                        .requestMatchers(HttpMethod.PUT, "/api/fairs/*/recruit-notice").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        // Application 도메인 - 신청/취소요청 제출·조회는 로그인만 필요(본인 소유 여부는 서비스 계층에서 검증)
                        .requestMatchers(HttpMethod.POST, "/api/fairs/*/applications").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/applications").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/applications/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/applications/*/cancel-requests").authenticated()
                        // Application 도메인 - 행사 담당자 전용(EVENT_ADMIN/SUPER_ADMIN). 담당 fair인지는
                        // FairAdminAccessGuard가 서비스 계층에서 한 번 더 확인한다.
                        .requestMatchers(HttpMethod.GET, "/api/fairs/*/applications").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/applications/*/approve").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/applications/*/reject").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/fairs/*/cancel-requests").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/applications/*/cancel-requests/approve").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/applications/*/cancel-requests/reject").hasAnyRole("EVENT_ADMIN", "SUPER_ADMIN")
                        // Booth 도메인 - 로그인만 하면 누구나 접근 가능(본인 소유 부스인지는 서비스 계층에서 검증)
                        .requestMatchers(HttpMethod.PUT, "/api/booths/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/booths/*/items").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/booth-items/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/booth-items/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/booths/favorites").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/booths/*/favorites").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/booths/*/favorites").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/booths/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/booths/visits/fairs").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/booths/visits").authenticated()
                        // Chat 도메인 - 비로그인 상담이 기본이라 전 구간 permitAll이다.
                        // 대화 소유는 X-Chat-Guest-Key 헤더로 증명하고, 일치 여부는
                        // ChatConversationService가 매 요청 검증한다(인증으로 막지 않는다).
                        // anyRequest().permitAll()에 이미 걸리지만, 기본값이 나중에
                        // authenticated()로 바뀌어도 위젯이 죽지 않도록 명시해 둔다.
                        .requestMatchers("/api/chat/**").permitAll()
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
