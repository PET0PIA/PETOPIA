package com.ms.petopia.global.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// OncePerRequestFilter를 상속하면 요청 하나당 딱 한 번 실행되는 필터가 된다.
// 서버로 들어오는 모든 HTTP 요청이 컨트롤러에 도달하기 전에 이 필터를 먼저 거쳐간다 (SecurityConfig에서 필터체인에 등록하면)
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // 클라이언트가 보내는 헤더 이름과, 그 값 앞에 붙는 접두사.
    // Authorization: Bearer eyJhbGciOiJIUzI1NiJ9....
    private static final String HEADER_NAME = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 헤더에서 토큰 문자열 뽑아온다
        String token = resolveToken(request);

        if (token != null) {
            try {
                // 토큰을 까서 userId와 role을 꺼낸다
                Long userId = jwtTokenProvider.getUserId(token);
                String role = jwtTokenProvider.getRole(token);

                // Spring Security가 이해하는 권한 형태로 변환
                List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

                // 인증 정보 객체를 만든다.
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userId, null, authorities);

                // SecurityContext에 심어둔다(@AuthenticationPrincipal 같은)
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    // Authorization: Bearer {토큰} 헤더에서 토큰 부분만 잘라낸다.
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_NAME);
        if (header != null && header.startsWith(TOKEN_PREFIX)) {
            return header.substring(TOKEN_PREFIX.length());
        }
        return null;
    }

}
