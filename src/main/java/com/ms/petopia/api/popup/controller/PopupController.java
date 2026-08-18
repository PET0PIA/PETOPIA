package com.ms.petopia.api.popup.controller;

import com.ms.petopia.api.popup.dto.response.PopupResponse;
import com.ms.petopia.api.popup.service.PopupService;
import com.ms.petopia.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/popups")
@RequiredArgsConstructor
public class PopupController {

    private final PopupService popupService;

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<PopupResponse>>> getActivePopups() {
        return ResponseEntity.ok(ApiResponse.success(popupService.getActiveList()));
    }
}
