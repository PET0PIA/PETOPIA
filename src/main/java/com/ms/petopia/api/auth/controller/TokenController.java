package com.ms.petopia.api.auth.controller;

import com.ms.petopia.api.auth.dto.LoginResponse;
import com.ms.petopia.api.auth.dto.TokenPair;
import com.ms.petopia.api.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class TokenController {

    private final TokenService tokenService;

    //새 토큰 발급
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@CookieValue String refreshToken) {
        TokenPair tokenPair = tokenService.refresh(refreshToken);

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokenPair.refreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(14))
                .build();

        return ResponseEntity.ok()
                //헤더에 쿠키 추가
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                //body는 json
                .body(new LoginResponse(tokenPair.accessToken()));
    }

    //로그아웃
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue String refreshToken) {
        tokenService.logout(refreshToken);

        //값을 비우고 maxAge(0)으로 보내면 브라우저가 즉시 쿠키를 지움
        ResponseCookie expiredCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }
}
