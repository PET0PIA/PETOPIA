package com.ms.petopia.api.auth.controller;

import com.ms.petopia.api.auth.dto.PasswordChangeRequest;
import com.ms.petopia.api.auth.dto.PasswordResetEmailRequest;
import com.ms.petopia.api.auth.dto.PasswordResetRequest;
import com.ms.petopia.api.auth.service.PasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/password")
public class PasswordController {

    private final PasswordService passwordService;

    //비밀번호 변경(마이페이지용)
    @PatchMapping("/change")
    public void changePassword(@AuthenticationPrincipal Long userId,
                               @Valid @RequestBody PasswordChangeRequest request) {
        passwordService.changePassword(userId, request.currentPassword(), request.newPassword());

    }

    //비밀번호 재설정 메일 보내기
    @PostMapping("/reset-request")
    public void requestPasswordReset(@Valid @RequestBody PasswordResetEmailRequest request) {
        passwordService.requestPasswordReset(request.email());
    }

    //비밀번호 재설정(비밀번호 분실용)
    @PostMapping("/reset")
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordService.resetPassword(request.token(), request.newPassword());
    }




}
