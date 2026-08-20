package com.ms.petopia.api.notice.domain;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoticeAttachment {
    private Long attachmentId;
    private Long noticeId;
    /** 확정된 공개 URL(S3 uploads). 배너/팝업 이미지와 같은 방식으로 저장된다. */
    private String fileUrl;
    private String originalName;
    private long fileSize;
    private int sortOrder;
    private LocalDateTime createdAt;
}
