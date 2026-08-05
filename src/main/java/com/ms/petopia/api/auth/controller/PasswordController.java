package com.ms.petopia.api.auth.controller;

import com.ms.petopia.api.auth.dto.PasswordChangeRequest;
import com.ms.petopia.api.auth.service.PasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/password")
public class PasswordController {

    private final PasswordService passwordService;

    @PatchMapping("/change")
    public void changePassword(@AuthenticationPrincipal Long userId,
                               @Valid @RequestBody PasswordChangeRequest request) {
        passwordService.changePassword(userId, request.currentPassword(), request.newPassword());

    }
}
