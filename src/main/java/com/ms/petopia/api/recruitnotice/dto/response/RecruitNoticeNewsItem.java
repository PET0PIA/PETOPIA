package com.ms.petopia.api.recruitnotice.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 소식 목록(notice 도메인)에 섞어 보여줄 모집공고 한 줄.
 *
 * <p>모집공고 본문은 이미 {@code /fairs/{fairId}/recruit-notice} 화면이 담당하므로 여기서는
 * 목록에 필요한 최소 정보만 넘긴다. 공지 테이블에 같은 내용을 복사하지 않기 위한 통로다.
 */
@Getter
@Setter
public class RecruitNoticeNewsItem {
    private Long recruitNoticeId;
    private Long fairId;
    private String fairName;
    private String title;
    private LocalDateTime createdAt;
}
