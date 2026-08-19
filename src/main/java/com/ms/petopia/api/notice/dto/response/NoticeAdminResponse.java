package com.ms.petopia.api.notice.dto.response;

import com.ms.petopia.api.notice.domain.Notice;
import com.ms.petopia.api.notice.domain.NoticeAttachment;
import com.ms.petopia.api.notice.domain.NoticeCategory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 화면용 응답. 목록과 단건이 같은 모양이다 - 관리자 화면은 목록을 받아두고 그 자리에서
 * 수정 폼을 열기 때문에, 목록 단계에서 본문·첨부까지 들고 있는 편이 왕복이 적다(배너·팝업과 동일).
 */
@Getter
@Builder
public class NoticeAdminResponse {

    private Long noticeId;
    private NoticeCategory category;
    private String title;
    private String content;
    private Long fairId;
    private String fairName;
    private boolean published;
    private boolean pinned;
    private int viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<NoticeAttachmentResponse> attachments;

    public static NoticeAdminResponse of(Notice notice, List<NoticeAttachment> attachments) {
        return NoticeAdminResponse.builder()
                .noticeId(notice.getNoticeId())
                .category(notice.getCategory())
                .title(notice.getTitle())
                .content(notice.getContent())
                .fairId(notice.getFairId())
                .fairName(notice.getFairName())
                .published(notice.isPublished())
                .pinned(notice.isPinned())
                .viewCount(notice.getViewCount())
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .attachments(attachments.stream().map(NoticeAttachmentResponse::from).toList())
                .build();
    }
}
