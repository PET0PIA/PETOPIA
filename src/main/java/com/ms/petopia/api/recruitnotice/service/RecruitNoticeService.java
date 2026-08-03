package com.ms.petopia.api.recruitnotice.service;

import com.ms.petopia.api.recruitnotice.domain.FairStatusInfo;
import com.ms.petopia.api.recruitnotice.domain.RecruitNotice;
import com.ms.petopia.api.recruitnotice.dto.request.RecruitNoticeRequest;
import com.ms.petopia.api.recruitnotice.dto.response.RecruitNoticeResponse;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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

    public RecruitNoticeResponse getNotice(Long fairId) {

        // 공고 조회, 없으면 예외
        RecruitNotice notice = recruitNoticeMapper.selectByFairId(fairId);

        if(notice == null) {
            throw new CommonException(ErrorCode.RECRUIT_NOTICE_NOT_FOUND);
        }

        // fairs 상태 조회
        FairStatusInfo fairStatus = recruitNoticeMapper.selectFairStatusByFairId(fairId);

        // recruitDeadline 지났는지 아닌지
        boolean deadlinePassed = LocalDateTime.now().isAfter(notice.getRecruitDeadline());
        // canceledAt 있는지(취소된 행사인지 확인)
        boolean fairCanceled = fairStatus.getCanceledAt() != null;
        // status = ENDED 인지(끝난 행사인지 확인)
        boolean fairEnded = "ENDED".equals(fairStatus.getStatus());

        // closed 계산
        boolean closed = deadlinePassed || fairCanceled || fairEnded;

        // TODO: boothSlots 조회 로직 추가 필요 (booth_slots 상태 판정 - 확정 부스 안내판과 로직 공유)

        // closed 계산하여 반환
        return RecruitNoticeResponse.from(notice, closed);

    }

}
