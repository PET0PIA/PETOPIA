package com.ms.petopia.api.recruitnotice.mapper;

import com.ms.petopia.api.recruitnotice.domain.FairStatusInfo;
import com.ms.petopia.api.recruitnotice.domain.RecruitNotice;
import com.ms.petopia.api.recruitnotice.dto.response.BoothSlotStatusResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface RecruitNoticeMapper {

    // 모집 공고 작성/수정
    void upsertNotice(RecruitNotice notice);

    // 해당 행사에 모집 공고가 있는지 확인
    RecruitNotice selectByFairId(@Param("fairId") Long fairId);

    // fair_admin_assignments에서 이 행사의 담당 EVENT_ADMIN 조회 (참조용)
    Long selectAdminUserIdByFairId(@Param("fairId") Long fairId);

    // 행사 취소/종료 상태 조회 (모집 공고 마감 판정용)
    FairStatusInfo selectFairStatusByFairId(@Param("fairId") Long fairId);

    // 특정 행사의 부스 슬롯 현황(AVAILABLE/PENDING/CONFIRMED) + 확정 업체명 조회
    List<BoothSlotStatusResponse> selectBoothSlotStatusesByFairId(@Param("fairId") Long fairId);

    // 넘긴 fairId들 중 모집중(isClosed 반대 기준)인 것만 반환
    Set<Long> selectRecruitingFairIds(@Param("fairIds") List<Long> fairIds);

}
