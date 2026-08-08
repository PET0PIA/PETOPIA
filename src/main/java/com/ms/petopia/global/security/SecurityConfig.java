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
                        // TODO 인증 도메인 완성 후 SUPER_ADMIN 권한 검증(JWT)으로 되돌린다.
                        // 지금은 다른 관리자 API들과 동일하게 X-User-Id 임시 헤더 방식(permitAll)을 쓴다 -
                        // AuditLogController 등 /api/admin/** 하위 컨트롤러가 이미 이 전제로 작성돼 있다.
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
