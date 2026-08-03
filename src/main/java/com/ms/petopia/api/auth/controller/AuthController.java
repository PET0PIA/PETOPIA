package com.ms.petopia.api.auth.controller;

import com.ms.petopia.api.auth.dto.EmailCheckResponse;
import com.ms.petopia.api.auth.dto.EmailSignupRequest;
import com.ms.petopia.api.auth.dto.EmailVerifyRequest;
import com.ms.petopia.api.auth.dto.EmailVerifyResendRequest;
import com.ms.petopia.api.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/email")
public class AuthController {

    private final AuthService authService;

    //회원가입
    @PostMapping("/signup")
    public void signup(@Valid @RequestBody  EmailSignupRequest request){
        authService.signup(request);
    }

    //이메일이 이미 존재하는지 확인
    @GetMapping("/check")
    public EmailCheckResponse isEmailAvailable(@RequestParam String email){
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
        authService.verifyEmail(request.token());
    }

}
