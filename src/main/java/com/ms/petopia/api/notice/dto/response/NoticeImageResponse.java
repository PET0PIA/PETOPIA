package com.ms.petopia.api.notice.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 에디터가 <img src>에 그대로 쓸 수 있는 확정된 공개 URL. */
@Getter
@Builder
public class NoticeImageResponse {
    private String url;
}
