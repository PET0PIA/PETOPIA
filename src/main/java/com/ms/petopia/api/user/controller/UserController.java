package com.ms.petopia.api.user.controller;

import com.ms.petopia.api.user.dto.UserMeResponse;
import com.ms.petopia.api.user.dto.UserUpdateRequest;
import com.ms.petopia.api.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;

    //내 프로필 조회
    @GetMapping
    public UserMeResponse getMe(@AuthenticationPrincipal Long userId) {
        return userService.getMe(userId);
    }

    //내 프로필 부분 수정
    @PatchMapping
    public UserMeResponse updateMe(@AuthenticationPrincipal Long userId, @Valid @RequestBody UserUpdateRequest request) {
        return userService.updateMe(userId, request);
    }
}
