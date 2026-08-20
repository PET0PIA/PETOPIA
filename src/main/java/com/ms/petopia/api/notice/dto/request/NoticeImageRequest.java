package com.ms.petopia.api.notice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 본문 에디터에 넣을 이미지의 임시 objectKey. 확정 후 공개 URL을 돌려받는다. */
@Getter
@Setter
public class NoticeImageRequest {

    @NotBlank
    @Size(max = 500)
    private String objectKey;
}
