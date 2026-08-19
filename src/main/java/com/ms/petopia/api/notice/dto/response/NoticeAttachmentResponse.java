package com.ms.petopia.api.notice.dto.response;

import com.ms.petopia.api.notice.domain.NoticeAttachment;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NoticeAttachmentResponse {

    private Long attachmentId;
    private String fileUrl;
    private String originalName;
    /** byte 단위. "2.4MB" 같은 표기는 화면에서 만든다. */
    private long fileSize;

    public static NoticeAttachmentResponse from(NoticeAttachment attachment) {
        return NoticeAttachmentResponse.builder()
                .attachmentId(attachment.getAttachmentId())
                .fileUrl(attachment.getFileUrl())
                .originalName(attachment.getOriginalName())
                .fileSize(attachment.getFileSize())
                .build();
    }
}
