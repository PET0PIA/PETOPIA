package com.ms.petopia.api.recruitnotice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/*
 * 참가업체 모집 공고 작성/수정 요청 (PUT /api/fairs/{fairId}/recruit-notice)
 * title, content, recruitDeadline은 필수
 * imageUrl은 선택 입력
 */
@Getter
@Setter
public class RecruitNoticeRequest {

    @NotBlank(message = "공고 제목은 필수입니다.")
    private String title;

    @NotBlank(message = "공고 본문은 필수입니다.")
    private String content;

    private String imageUrl; // 대표 이미지 url, 선택 입력

    @NotNull(message = "모집 마감일시는 필수입니다.")
    private LocalDateTime recruitDeadline;

}
