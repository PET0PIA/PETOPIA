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
    /**
     * 행사 배지를 링크로 걸어도 되는지. 아직 전체공개 안 된 행사·취소된 행사는 공개 상세가 404라서,
     * 화면은 이 값이 false면 링크 없이 이름만 보여준다(눌러도 안 열리는 링크 방지).
     */
    private boolean fairPublic;
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
                .fairPublic(notice.isFairPublic())
                .pinned(notice.isPinned())
                .viewCount(notice.getViewCount())
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .attachments(attachments.stream().map(NoticeAttachmentResponse::from).toList())
                .build();
    }
}
