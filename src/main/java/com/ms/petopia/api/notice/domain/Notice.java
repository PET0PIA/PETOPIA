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

    /**
     * 연결된 행사를 관람객이 열어볼 수 있는지. 조인으로 계산하는 값이고 notice 컬럼이 아니다.
     *
     * <p>공개 상세({@code GET /api/fairs/{id}/public})가 아직 전체공개 안 된 행사와 취소된 행사를
     * 404로 막기 때문에, 화면이 행사 배지를 링크로 걸어도 되는지 여기서 미리 알려준다. 판정 기준은
     * FairService#getPublicSummary와 <b>같은 조건</b>(published_at 있음 + canceled_at 없음)이라야
     * 하고, 한쪽만 바뀌면 다시 눌러도 안 열리는 링크가 생긴다.
     */
    private boolean fairPublic;
}
