package com.ms.petopia.api.popup.controller;

import com.ms.petopia.api.popup.dto.request.PopupCreateRequest;
import com.ms.petopia.api.popup.dto.request.PopupUpdateRequest;
import com.ms.petopia.api.popup.dto.response.PopupResponse;
import com.ms.petopia.api.popup.service.PopupService;
import com.ms.petopia.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/popups")
@RequiredArgsConstructor
public class AdminPopupController {

    private final PopupService popupService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PopupResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(popupService.getAll()));
    }

    @GetMapping("/{popupId}")
    public ResponseEntity<ApiResponse<PopupResponse>> getById(@PathVariable Long popupId) {
        return ResponseEntity.ok(ApiResponse.success(popupService.getById(popupId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PopupResponse>> create(
            @AuthenticationPrincipal Long callerId,
            @Valid @RequestBody PopupCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, popupService.create(callerId, request)));
    }

    @PutMapping("/{popupId}")
    public ResponseEntity<ApiResponse<PopupResponse>> update(
            @PathVariable Long popupId,
            @Valid @RequestBody PopupUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(popupService.update(popupId, request)));
    }

    @DeleteMapping("/{popupId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long popupId) {
        popupService.delete(popupId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{popupId}/toggle")
    public ResponseEntity<ApiResponse<Void>> toggleActive(
            @PathVariable Long popupId,
            @RequestParam boolean isActive) {
        popupService.toggleActive(popupId, isActive);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
