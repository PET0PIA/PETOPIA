package com.ms.petopia.api.recruitnotice.dto.request;

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

    private String title;
    private String content;
    private String imageUrl; // 선택 입력
    private LocalDateTime recruitDeadline;

}
