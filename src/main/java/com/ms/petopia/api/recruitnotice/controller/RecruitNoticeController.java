package com.ms.petopia.api.recruitnotice.controller;

import com.ms.petopia.api.recruitnotice.dto.request.RecruitNoticeRequest;
import com.ms.petopia.api.recruitnotice.dto.response.RecruitNoticeResponse;
import com.ms.petopia.api.recruitnotice.service.RecruitNoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fairs/{fairId}")
@RequiredArgsConstructor
public class RecruitNoticeController {

    private final RecruitNoticeService recruitNoticeService;

    // 모집 공고 작성/수정
    // TODO: 인증 붙으면 @PreAuthorize("hasRole('EVENT_ADMIN')") 추가
    @PutMapping("/recruit-notice")
    public RecruitNoticeResponse upsertNotice(
            @PathVariable Long fairId,
            @RequestHeader(RecruitNoticeTemporaryAuthHeaders.USER_ID) Long writerId,
            @Valid @RequestBody RecruitNoticeRequest request
    ) {

        return recruitNoticeService.upsertNotice(fairId, writerId, request);

    }

    // 모집 공고 상세 조회
    @GetMapping("/recruit-detail")
    public RecruitNoticeResponse getNotice(@PathVariable Long fairId) {

        return recruitNoticeService.getNotice(fairId);

    }

}
