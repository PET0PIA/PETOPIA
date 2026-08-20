package com.ms.petopia.api.notice.controller;

import com.ms.petopia.api.notice.dto.request.NoticeImageRequest;
import com.ms.petopia.api.notice.dto.request.NoticeSaveRequest;
import com.ms.petopia.api.notice.dto.response.NoticeAdminResponse;
import com.ms.petopia.api.notice.dto.response.NoticeImageResponse;
import com.ms.petopia.api.notice.service.NoticeAdminService;
import com.ms.petopia.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 공지사항 관리 API. SecurityConfig의 {@code /api/admin/**} 규칙으로 SUPER_ADMIN만 도달한다.
 */
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final NoticeAdminService noticeAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NoticeAdminResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(noticeAdminService.getAll()));
    }

    @GetMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<NoticeAdminResponse>> getById(@PathVariable Long noticeId) {
        return ResponseEntity.ok(ApiResponse.success(noticeAdminService.getById(noticeId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NoticeAdminResponse>> create(
            @AuthenticationPrincipal Long callerId,
            @Valid @RequestBody NoticeSaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, noticeAdminService.create(callerId, request)));
    }

    @PutMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<NoticeAdminResponse>> update(
            @PathVariable Long noticeId,
            @Valid @RequestBody NoticeSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(noticeAdminService.update(noticeId, request)));
    }

    @DeleteMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long noticeId) {
        noticeAdminService.delete(noticeId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{noticeId}/publish")
    public ResponseEntity<ApiResponse<Void>> setPublished(
            @PathVariable Long noticeId,
            @RequestParam boolean isPublished) {
        noticeAdminService.setPublished(noticeId, isPublished);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{noticeId}/pin")
    public ResponseEntity<ApiResponse<Void>> setPinned(
            @PathVariable Long noticeId,
            @RequestParam boolean isPinned) {
        noticeAdminService.setPinned(noticeId, isPinned);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 본문 에디터용 - 임시 업로드된 이미지를 확정하고 바로 쓸 수 있는 URL을 돌려준다. */
    @PostMapping("/images")
    public ResponseEntity<ApiResponse<NoticeImageResponse>> confirmContentImage(
            @Valid @RequestBody NoticeImageRequest request) {
        String url = noticeAdminService.confirmContentImage(request.getObjectKey());
        return ResponseEntity.ok(ApiResponse.success(NoticeImageResponse.builder().url(url).build()));
    }
}
