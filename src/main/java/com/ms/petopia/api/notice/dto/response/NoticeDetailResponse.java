package com.ms.petopia.api.notice.dto.response;

import com.ms.petopia.api.notice.domain.Notice;
import com.ms.petopia.api.notice.domain.NoticeAttachment;
import com.ms.petopia.api.notice.domain.NoticeCategory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class NoticeDetailResponse {

    private Long noticeId;
    private NoticeCategory category;
    private String title;
    /** HTML. 화면에서 정화(DOMPurify) 후 렌더해야 한다. */
    private String content;
    private Long fairId;
    private String fairName;
    private boolean pinned;
    private int viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<NoticeAttachmentResponse> attachments;

    public static NoticeDetailResponse of(Notice notice, List<NoticeAttachment> attachments) {
        return NoticeDetailResponse.builder()
                .noticeId(notice.getNoticeId())
                .category(notice.getCategory())
                .title(notice.getTitle())
                .content(notice.getContent())
                .fairId(notice.getFairId())
                .fairName(notice.getFairName())
                .pinned(notice.isPinned())
                .viewCount(notice.getViewCount())
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .attachments(attachments.stream().map(NoticeAttachmentResponse::from).toList())
                .build();
    }
}
