package com.ms.petopia.api.recruitnotice.service;

import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.recruitnotice.domain.FairStatusInfo;
import com.ms.petopia.api.recruitnotice.domain.RecruitNotice;
import com.ms.petopia.api.recruitnotice.dto.request.RecruitNoticeRequest;
import com.ms.petopia.api.recruitnotice.dto.response.BoothSlotStatusResponse;
import com.ms.petopia.api.recruitnotice.dto.response.RecruitNoticeResponse;
import com.ms.petopia.api.recruitnotice.dto.response.RecruitNoticeUpsertResponse;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecruitNoticeService {

    private final RecruitNoticeMapper recruitNoticeMapper;
    private final StorageService storageService;
    private final FairAdminAccessGuard fairAdminAccessGuard;

    // 모집 공고 작성/수정
    public RecruitNoticeUpsertResponse upsertNotice(Long fairId, RecruitNoticeRequest request) {

        fairAdminAccessGuard.checkAssigned(fairId);

        // 모집 공고에 기록할 작성자 = 현재 이 행사의 담당자(SUPER_ADMIN이 대신 수정해도 담당자 이름으로 저장됨)
        Long fairAdminUserId = recruitNoticeMapper.selectAdminUserIdByFairId(fairId);

        // 해당 행사에 담당자가 없을 경우(방어 코드)
        if (fairAdminUserId == null) {
            throw new CommonException(ErrorCode.RECRUIT_NOTICE_ACCESS_DENIED, "담당자가 배정되지 않은 행사입니다.");
        }

        RecruitNotice notice = RecruitNotice.builder()
                .fairId(fairId)
                .writerId(fairAdminUserId)
                .title(request.getTitle())
                .content(request.getContent())
                .imageUrl(resolveImageUrl(request.getImageObjectKey()))
                .recruitDeadline(request.getRecruitDeadline())
                .build();

        // upsert
        recruitNoticeMapper.upsertNotice(notice);

        // 재조회(데이터 정확성을 위해)
        RecruitNotice saved = recruitNoticeMapper.selectByFairId(fairId);

        return RecruitNoticeUpsertResponse.from(saved);

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

        // 부스 슬롯 현황 + 확정 업체명 조회
        List<BoothSlotStatusResponse> boothSlots =
                recruitNoticeMapper.selectBoothSlotStatusesByFairId(fairId);

        // closed 계산하여 반환
        return RecruitNoticeResponse.from(notice, isClosed(notice, fairStatus), boothSlots);

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

    /*
     * presigned 업로드로 받은 임시 객체 키를 확정(tmp -> uploads)하고 공개 URL로 바꾼다.
     * 키가 없으면(이미지를 첨부하지 않았으면) null을 그대로 반환한다.
     */
    private String resolveImageUrl(String temporaryObjectKey) {

        if (temporaryObjectKey == null || temporaryObjectKey.isBlank()) {
            return null;
        }

        String confirmedKey = storageService.confirm(temporaryObjectKey, UploadPolicy.IMAGE);

        return storageService.toPublicUrl(confirmedKey);
    }

    /*
     * 여러 행사 중 지금 참가업체 모집중인 행사 ID만 골라 반환한다. isClosed()와 정확히
     * 반대 기준(모집공고 존재 + 마감 전 + 행사 취소/종료 아님)이라, "모집중" 판단 기준이
     * 이 도메인 한 곳에만 있도록 유지한다 - 여러 행사를 한 번에 다뤄야 하는 곳(행사 목록 등)은
     * 직접 판정하지 말고 이 메서드를 재사용해야 기준이 어긋나지 않는다.
     */
    public Set<Long> getRecruitingFairIds(List<Long> fairIds) {

        if (fairIds == null || fairIds.isEmpty()) {
            return Set.of();
        }

        return recruitNoticeMapper.selectRecruitingFairIds(fairIds);

    }

}
