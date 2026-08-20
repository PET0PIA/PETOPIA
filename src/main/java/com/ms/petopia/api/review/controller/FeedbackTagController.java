package com.ms.petopia.api.review.controller;

import com.ms.petopia.api.review.dto.CreateFeedbackTagRequest;
import com.ms.petopia.api.review.dto.FeedbackTag;
import com.ms.petopia.api.review.dto.FeedbackTagResponse;
import com.ms.petopia.api.review.dto.FeedbackTagUsageResponse;
import com.ms.petopia.api.review.dto.UpdateFeedbackTagRequest;
import com.ms.petopia.api.review.service.FeedbackTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 태그 마스터(feedback_tags) API.
 *
 * <p>활성 목록 조회는 permitAll이다 - 리뷰 마법사가 로그인 여부와 무관하게 태그 선택지를
 * 보여줘야 한다. 등록·수정·사용현황 조회는 SUPER_ADMIN만(SecurityConfig).
 */
@RestController
@RequestMapping("/api/feedback-tags")
@RequiredArgsConstructor
public class FeedbackTagController {

    private final FeedbackTagService feedbackTagService;

    @GetMapping
    public List<FeedbackTagResponse> listActive(@RequestParam FeedbackTag.Scope scope) {
        return feedbackTagService.listActive(scope);
    }

    @PostMapping
    public ResponseEntity<FeedbackTagResponse> create(@RequestBody CreateFeedbackTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedbackTagService.create(request));
    }

    @PatchMapping("/{tagId}")
    public FeedbackTagResponse update(@PathVariable Long tagId, @RequestBody UpdateFeedbackTagRequest request) {
        return feedbackTagService.update(tagId, request);
    }

    @GetMapping("/{tagId}/usage")
    public FeedbackTagUsageResponse usage(@PathVariable Long tagId) {
        return feedbackTagService.getUsage(tagId);
    }
}
