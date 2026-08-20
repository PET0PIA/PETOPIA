package com.ms.petopia.api.notice.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 첨부파일 한 개. 두 가지 중 하나로 온다.
 * <ul>
 *   <li>{@code attachmentId}만 있음 - 수정 화면에서 <b>기존 첨부를 그대로 두겠다</b>는 뜻</li>
 *   <li>{@code objectKey} + 파일명 + 크기 - <b>새로 올린 파일</b>. 저장 시점에 확정 처리된다</li>
 * </ul>
 * 목록에서 빠진 기존 첨부는 삭제된다.
 */
@Getter
@Setter
public class NoticeAttachmentRequest {

    private Long attachmentId;

    @Size(max = 500)
    private String objectKey;

    @Size(max = 255)
    private String originalName;

    private Long fileSize;

    @AssertTrue(message = "기존 첨부(attachmentId) 또는 새 첨부(objectKey·originalName·fileSize) 중 하나여야 합니다.")
    public boolean isEitherKeptOrNew() {
        boolean kept = attachmentId != null;
        boolean uploaded = objectKey != null && !objectKey.isBlank()
                && originalName != null && !originalName.isBlank()
                && fileSize != null && fileSize > 0;
        return kept ^ uploaded;
    }
}
