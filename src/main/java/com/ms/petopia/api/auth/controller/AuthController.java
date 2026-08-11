package com.ms.petopia.api.auth.controller;

import com.ms.petopia.api.auth.dto.EmailCheckResponse;
import com.ms.petopia.api.auth.dto.EmailLoginRequest;
import com.ms.petopia.api.auth.dto.EmailSignupRequest;
import com.ms.petopia.api.auth.dto.EmailVerifyRequest;
import com.ms.petopia.api.auth.dto.EmailVerifyResendRequest;
import com.ms.petopia.api.auth.dto.LoginResponse;
import com.ms.petopia.api.auth.dto.TokenPair;
import com.ms.petopia.api.auth.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/email")
public class AuthController {

    private final AuthService authService;

    // 로컬(http)은 false, 운영(https)은 true - application-{profile}.yaml의 cookie.secure 참고
    @Value("${cookie.secure}")
    private boolean cookieSecure;

    //회원가입
    @PostMapping("/signup")
    public void signup(@Valid @RequestBody  EmailSignupRequest request){
        authService.signup(request);
    }

    //이메일이 이미 존재하는지 확인
    @GetMapping("/check")
    public EmailCheckResponse isEmailAvailable(@RequestParam @NotBlank @Email String email){
        return authService.isEmailAvailable(email);
    }

    //이메일 인증 재전송
    @PostMapping("/verify/resend")
    public void resendVerification(@Valid @RequestBody EmailVerifyResendRequest request) {
        authService.resendVerification(request.email());
    }

    //이메일 인증 코드 검증
    @PostMapping("/verify")
    public void verifyEmail(@Valid @RequestBody EmailVerifyRequest request) {
        authService.verifyEmail(request.email(), request.token());
    }

    //로그인
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody EmailLoginRequest request) {
        TokenPair tokenPair = authService.login(request);

        //쿠키로 refreshToken 저장
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokenPair.refreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
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

}
