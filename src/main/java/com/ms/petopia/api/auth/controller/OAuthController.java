package com.ms.petopia.api.auth.controller;

import com.ms.petopia.api.auth.dto.LoginResponse;
import com.ms.petopia.api.auth.dto.OAuthExchangeRequest;
import com.ms.petopia.api.auth.dto.OAuthSignupRequest;
import com.ms.petopia.api.auth.dto.TokenPair;
import com.ms.petopia.api.auth.service.OAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    private final OAuthService oAuthService;

    //구글 로그인/동의 화면으로 브라우저를 리다이렉트.
    //JSON을 리턴하는 게 아니라 302 응답 + Location 헤더로 갈 곳 알려줌
    @GetMapping("/oauth/{provider}/login")
    public ResponseEntity<Void> login(@PathVariable String provider) {
        String authorizationUrl = oAuthService.getAuthorizationUrl(provider);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authorizationUrl))
                .build();
    }

    //구글이 유저 인증 마치고 브라우저를 돌려보내는 지점.
    //JSON이 아니라 프론트 쪽 URL로 302 리다이렉트
    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider, @RequestParam String code, @RequestParam String state
    ) {
        String redirectUrl = oAuthService.handleCallback(provider, code, state);
        return ResponseEntity.status(HttpStatus.FOUND)
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
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(14))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new LoginResponse(tokenPair.accessToken()));
    }
}
