package com.ms.petopia.api.recruitnotice.service;

import com.ms.petopia.api.recruitnotice.domain.RecruitNotice;
import com.ms.petopia.api.recruitnotice.dto.request.RecruitNoticeRequest;
import com.ms.petopia.api.recruitnotice.dto.response.RecruitNoticeResponse;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecruitNoticeService {

    private final RecruitNoticeMapper recruitNoticeMapper;

    // 모집 공고 작성/수정
    public RecruitNoticeResponse upsertNotice(Long fairId, Long writerId, RecruitNoticeRequest request) {

        // 해당 행사의 담당자가 작성하는게 맞는지 확인
        Long fairAdminUserId = recruitNoticeMapper.selectAdminUserIdByFairId(fairId);

        // 해당 행사 또는 해당 행사의 담당자가 없을 경우(방어 코드)
        if (fairAdminUserId == null) {
            throw new CommonException(ErrorCode.RECRUIT_NOTICE_ACCESS_DENIED, "담당자가 배정되지 않은 행사입니다.");
        }

        // 모집 공고 작성자가 해당 행사의 담당자가 아닐 경우
        if (!fairAdminUserId.equals(writerId)) {
            throw new CommonException(ErrorCode.RECRUIT_NOTICE_ACCESS_DENIED, "본인이 담당하는 행사가 아닙니다.");
        }

        // 기존 공고 있는지 확인 + 작성자 검증
        RecruitNotice existing = recruitNoticeMapper.selectByFairId(fairId);

        if(existing != null && !existing.getWriterId().equals(writerId)) {
            throw new CommonException(ErrorCode.RECRUIT_NOTICE_ACCESS_DENIED);
        }

        RecruitNotice notice = RecruitNotice.builder()
                .fairId(fairId)
                .writerId(writerId)
                .title(request.getTitle())
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .recruitDeadline(request.getRecruitDeadline())
                .build();

        // upsert
        recruitNoticeMapper.upsertNotice(notice);

        // 재조회(데이터 정확성을 위해)
        RecruitNotice saved = recruitNoticeMapper.selectByFairId(fairId);

        // closed 임시로 false
        return RecruitNoticeResponse.from(saved, false);

    }

}
