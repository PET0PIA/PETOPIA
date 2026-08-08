package com.ms.petopia.api.auth.controller;

import com.ms.petopia.api.auth.dto.LoginResponse;
import com.ms.petopia.api.auth.dto.OAuthAuthorizationStart;
import com.ms.petopia.api.auth.dto.OAuthExchangeRequest;
import com.ms.petopia.api.auth.dto.OAuthSignupRequest;
import com.ms.petopia.api.auth.dto.TokenPair;
import com.ms.petopia.api.auth.service.OAuthService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class OAuthController {

    //로그인 CSRF 방지용 상관관계 쿠키 이름. /login에서 심고 /callback에서 대조 후 지운다.
    private static final String STATE_COOKIE_NAME = "oauth_state";

    private final OAuthService oAuthService;

    // 로컬(http)은 false, 운영(https)은 true - application-{profile}.yaml의 cookie.secure 참고
    @Value("${cookie.secure}")
    private boolean cookieSecure;

    //구글 로그인/동의 화면으로 브라우저를 리다이렉트.
    //JSON을 리턴하는 게 아니라 302 응답 + Location 헤더로 갈 곳 알려줌.
    //동시에 state를 HttpOnly 쿠키로도 심어둔다
    // /callback에서 이 브라우저가 로그인을 시작한 브라우저인지 대조하기 위함 (Redis 검증만으로는 요청 출처까지는 못 막음)
    @GetMapping("/oauth/{provider}/login")
    public ResponseEntity<Void> login(@PathVariable String provider) {
        OAuthAuthorizationStart start = oAuthService.getAuthorizationUrl(provider);

        ResponseCookie stateCookie = ResponseCookie.from(STATE_COOKIE_NAME, start.state())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")    //최상위 GET 이동 일 땐 쿠키를 실어줌
                .path("/api/auth/oauth")
                .maxAge(Duration.ofMinutes(5))
                .build();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .location(URI.create(start.url()))
                .build();
    }

    //구글이 유저 인증 마치고 브라우저를 돌려보내는 지점.
    //JSON이 아니라 프론트 쪽 URL로 302 리다이렉트
    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state,
            @CookieValue(value = STATE_COOKIE_NAME, required = false) String stateCookie
    ) {
        if (stateCookie == null || !stateCookie.equals(state)) {
            throw new CommonException(ErrorCode.OAUTH_INVALID_STATE);
        }

        //OAuthStateStore.validate(state)가 handleCallback 내부에서 Redis 값을 원자적으로 조회+삭제(GETDEL)한다
        String redirectUrl = oAuthService.handleCallback(provider, code, state);

        //성공했으니 상관관계 쿠키는 더 필요 없음 값 비우고 즉시 만료시켜 브라우저에서 지움
        ResponseCookie expiredStateCookie = ResponseCookie.from(STATE_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth/oauth")
                .maxAge(0)
                .build();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, expiredStateCookie.toString())
                .location(URI.create(redirectUrl))
                .build();
    }

    //기존 유저 로그인 마무리(refreshToken 쿠키 + accessToken JSON)
    @PostMapping("/oauth/exchange")
    public ResponseEntity<LoginResponse> exchange(@Valid @RequestBody OAuthExchangeRequest request) {
        TokenPair tokenPair = oAuthService.exchangeLogin(request.code());
        return tokenResponse(tokenPair);
    }

    //신규 유저 가입 마무리
    @PostMapping("/signup/complete")
    public ResponseEntity<LoginResponse> signupComplete(@Valid @RequestBody OAuthSignupRequest request) {
        TokenPair tokenPair = oAuthService.completeSignup(request);
        return tokenResponse(tokenPair);
    }

    //exchange/signupComplete 둘 다 "토큰 발급 후 같은 형태로 응답"하는 게 겹쳐서 뽑아둔 헬퍼.
    private ResponseEntity<LoginResponse> tokenResponse(TokenPair tokenPair) {
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokenPair.refreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(14))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                //토큰이 담긴 응답이 중간 캐시/브라우저 캐시에 저장되지 않게 함
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new LoginResponse(tokenPair.accessToken()));
    }
}
