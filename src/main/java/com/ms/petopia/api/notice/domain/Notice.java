package com.ms.petopia.api.notice.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notice {
    private Long noticeId;
    private NoticeCategory category;
    private String title;
    /** 에디터가 만든 HTML. 화면에 뿌릴 때 반드시 정화(sanitize)해야 한다. */
    private String content;
    private Long fairId;
    private boolean isPublished;
    private boolean isPinned;
    private int viewCount;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 조인으로 채우는 표시용 값(fairs.name). notice 테이블 컬럼이 아니다. */
    private String fairName;
}
