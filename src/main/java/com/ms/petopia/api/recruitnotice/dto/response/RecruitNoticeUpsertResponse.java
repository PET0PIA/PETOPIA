package com.ms.petopia.api.recruitnotice.dto.response;

import com.ms.petopia.api.recruitnotice.domain.RecruitNotice;
import lombok.*;

import java.time.LocalDateTime;

// PUT /api/fairs/{fairId}/recruit-notice 응답 (작성/수정 결과만, 명세서 기준 5개 필드)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecruitNoticeUpsertResponse {

    private Long recruitNoticeId;
    private Long fairId;
    private String title;
    private LocalDateTime recruitDeadline;
    private LocalDateTime updatedAt;

    public static RecruitNoticeUpsertResponse from(RecruitNotice notice) {

        return RecruitNoticeUpsertResponse.builder()
                .recruitNoticeId(notice.getRecruitNoticeId())
                .fairId(notice.getFairId())
                .title(notice.getTitle())
                .recruitDeadline(notice.getRecruitDeadline())
                .updatedAt(notice.getUpdatedAt())
                .build();

    }

}
