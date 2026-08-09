package com.ms.petopia.api.auth.controller;

import com.ms.petopia.api.auth.dto.AdminAccountListItemResponse;
import com.ms.petopia.api.auth.dto.AdminAccountStatusUpdateRequest;
import com.ms.petopia.api.auth.dto.EmailLoginRequest;
import com.ms.petopia.api.auth.dto.LoginResponse;
import com.ms.petopia.api.auth.dto.TokenPair;
import com.ms.petopia.api.auth.service.AdminAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

// SUPER_ADMIN 전용 로그인 + 행사 관리자 계정 관리.

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminAccountService adminAccountService;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    //SUPER_ADMIN 로그인
    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody EmailLoginRequest request) {
        TokenPair tokenPair = adminAccountService.adminLogin(request);

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokenPair.refreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(14))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new LoginResponse(tokenPair.accessToken()));
    }

    //EVENT_ADMIN 계정 목록 조회
    @GetMapping("/accounts")
    public List<AdminAccountListItemResponse> getAccounts() {
        return adminAccountService.getAdminAccounts();
    }

    //관리자 계정 정지/정지 해제
    @PatchMapping("/accounts/{userId}/status")
    public void updateAccountStatus(
            @PathVariable Long userId,
            @Valid @RequestBody AdminAccountStatusUpdateRequest request
    ) {
        adminAccountService.updateAccountStatus(userId, request.getStatus());
    }
}
