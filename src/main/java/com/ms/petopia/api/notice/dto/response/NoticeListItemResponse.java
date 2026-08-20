package com.ms.petopia.api.notice.dto.response;

import com.ms.petopia.api.notice.domain.Notice;
import com.ms.petopia.api.notice.domain.NoticeCategory;
import com.ms.petopia.api.recruitnotice.dto.response.RecruitNoticeNewsItem;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 소식 목록의 한 줄. 공지(notice)와 모집공고(recruit_notice) 두 출처가 같은 모양으로 나간다.
 *
 * <p>프론트가 출처를 따지지 않아도 되도록 {@code linkPath}(이동할 주소)를 서버가 정해서 내려준다.
 * 두 출처의 {@code id}는 서로 겹칠 수 있으므로, 화면에서 목록 key를 만들 때는
 * {@code category + id}처럼 조합해서 써야 한다.
 */
@Getter
@Builder
public class NoticeListItemResponse {

    private Long id;
    private NoticeCategory category;
    private String title;
    /** 연결된 행사가 없으면 null (전체 대상 공지). */
    private Long fairId;
    private String fairName;
    private boolean pinned;
    private int viewCount;
    private LocalDateTime createdAt;
    /** 클릭 시 이동할 주소. 공지는 /news/{id}, 모집공고는 기존 상세 화면으로 보낸다. */
    private String linkPath;

    public static NoticeListItemResponse from(Notice notice) {
        return NoticeListItemResponse.builder()
                .id(notice.getNoticeId())
                .category(notice.getCategory())
                .title(notice.getTitle())
                .fairId(notice.getFairId())
                .fairName(notice.getFairName())
                .pinned(notice.isPinned())
                .viewCount(notice.getViewCount())
                .createdAt(notice.getCreatedAt())
                .linkPath("/news/" + notice.getNoticeId())
                .build();
    }

    public static NoticeListItemResponse from(RecruitNoticeNewsItem item) {
        return NoticeListItemResponse.builder()
                .id(item.getRecruitNoticeId())
                .category(NoticeCategory.RECRUIT)
                .title(item.getTitle())
                .fairId(item.getFairId())
                .fairName(item.getFairName())
                // 모집공고는 고정 대상이 아니고, 조회수도 그쪽에서 세지 않는다.
                .pinned(false)
                .viewCount(0)
                .createdAt(item.getCreatedAt())
                .linkPath("/fairs/" + item.getFairId() + "/recruit-notice")
                .build();
    }
}
