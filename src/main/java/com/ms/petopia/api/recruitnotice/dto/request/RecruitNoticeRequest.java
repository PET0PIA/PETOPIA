package com.ms.petopia.api.recruitnotice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/*
 * 참가업체 모집 공고 작성/수정 요청 (PUT /api/fairs/{fairId}/recruit-notice)
 * title, content, recruitDeadline은 필수
 * imageObjectKey는 선택 입력
 */
@Getter
@Setter
public class RecruitNoticeRequest {

    @NotBlank(message = "공고 제목은 필수입니다.")
    @Size(max = 200, message = "공고 제목은 200자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "공고 본문은 필수입니다.")
    private String content;

    @Size(max = 500, message = "이미지 키는 500자 이하여야 합니다.")
    private String imageObjectKey; // presigned-upload로 받은 임시 객체 키, 선택 입력

    @NotNull(message = "모집 마감일시는 필수입니다.")
    private LocalDateTime recruitDeadline;

}
