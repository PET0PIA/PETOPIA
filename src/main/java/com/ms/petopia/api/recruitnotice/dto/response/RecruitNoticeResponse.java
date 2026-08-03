package com.ms.petopia.api.recruitnotice.dto.response;

import com.ms.petopia.api.recruitnotice.domain.RecruitNotice;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecruitNoticeResponse {

    private Long recruitNoticeId;
    private Long fairId;
    private String title;
    private String content;
    private String imageUrl;
    private LocalDateTime recruitDeadline;
    private LocalDateTime updatedAt;

    // 저장하지 않고 조회 시점마다 계산되는 값. true면 신청 버튼 비활성화 대상
    private boolean closed;

    /*
     * TODO: boothSlots 추가 필요 (부스 슬롯 현황 - AVAILABLE/PENDING/CONFIRMED)
     * 확정 부스 안내판 기능이랑 같이 별도 작업으로 진행 예정
     */

    public static RecruitNoticeResponse from(RecruitNotice notice, boolean closed) {

        return RecruitNoticeResponse.builder()
                .recruitNoticeId(notice.getRecruitNoticeId())
                .fairId(notice.getFairId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .imageUrl(notice.getImageUrl())
                .recruitDeadline(notice.getRecruitDeadline())
                .updatedAt(notice.getUpdatedAt())
                .closed(closed)
                .build();

    }

}
