package com.ms.petopia.api.notice.controller;

import com.ms.petopia.api.notice.domain.NoticeCategory;
import com.ms.petopia.api.notice.dto.response.NoticeDetailResponse;
import com.ms.petopia.api.notice.dto.response.NoticeListItemResponse;
import com.ms.petopia.api.notice.service.NoticeService;
import com.ms.petopia.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관람객용 소식·이벤트 공개 API. SecurityConfig의 anyRequest().permitAll()에 걸려
 * 비로그인도 볼 수 있다(배너/팝업 공개 API와 동일).
 */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NoticeListItemResponse>>> getList(
            @RequestParam(required = false) NoticeCategory category) {
        return ResponseEntity.ok(ApiResponse.success(noticeService.getPublicList(category)));
    }

    @GetMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<NoticeDetailResponse>> getDetail(@PathVariable Long noticeId) {
        return ResponseEntity.ok(ApiResponse.success(noticeService.getPublicDetail(noticeId)));
    }
}
