package com.ms.petopia.api.recruitnotice.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecruitNotice {

    private Long recruitNoticeId;
    private Long writerId; // 작성한 EVENT_ADMIN
    private Long fairId; // 행사당 1건(UK)
    private String title; // 공고 제목
    private String content; // 모집요강·지원자격·문의처 등
    private String imageUrl; // 공고 대표 이미지, 선택
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime recruitDeadline; // 모집 마감일시, 조기마감 가능

}
