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
            throw new CommonException(ErrorCode.RECRUIT_NOTICE_ACCESS_DENIED, "본인이 작성한 공고만 수정할 수 있습니다.");
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

        // closed 계산(upsertNotice, getNotice 공통 기준 사용)
        FairStatusInfo fairStatus = recruitNoticeMapper.selectFairStatusByFairId(fairId);

        return RecruitNoticeResponse.from(saved, isClosed(saved, fairStatus));

    }

    // 모집 공고 상세 조회
    public RecruitNoticeResponse getNotice(Long fairId) {

        // 공고 조회, 없으면 예외
        RecruitNotice notice = recruitNoticeMapper.selectByFairId(fairId);

        if(notice == null) {
            throw new CommonException(ErrorCode.RECRUIT_NOTICE_NOT_FOUND);
        }

        // fairs 상태 조회
        FairStatusInfo fairStatus = recruitNoticeMapper.selectFairStatusByFairId(fairId);

        // TODO: boothSlots 조회 로직 추가 필요 (booth_slots 상태 판정 - 확정 부스 안내판과 로직 공유)

        // closed 계산하여 반환
        return RecruitNoticeResponse.from(notice, isClosed(notice, fairStatus));

    }

    /*
     * 모집 공고 마감 여부를 판정한다.
     * upsertNotice, getNotice 양쪽에서 같은 기준으로 판정하도록 공통 메서드로 뽑았다.
     * 다음 중 하나라도 해당하면 마감으로 본다:
     * 1) 지금이 recruitDeadline 이후이거나 정확히 같음(경계값 포함, isBefore의 부정을 사용)
     * 2) 행사가 취소됨(canceledAt 있음)
     * 3) 행사가 종료됨(status = ENDED)
     */
    private boolean isClosed(RecruitNotice notice, FairStatusInfo fairStatus) {

        boolean deadlinePassed = !LocalDateTime.now().isBefore(notice.getRecruitDeadline());
        boolean fairCanceled = fairStatus.getCanceledAt() != null;
        boolean fairEnded = "ENDED".equals(fairStatus.getStatus());

        return deadlinePassed || fairCanceled || fairEnded;

    }

}
