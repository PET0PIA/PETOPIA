package com.ms.petopia.api.recruitnotice.mapper;

import com.ms.petopia.api.recruitnotice.domain.RecruitNotice;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RecruitNoticeMapper {

    // 모집 공고 작성/수정
    void upsertNotice(RecruitNotice notice);

    // 해당 행사에 모집 공고가 있는지 확인
    RecruitNotice selectByFairId(Long fairId);

    // fair_admin_assignments에서 이 행사의 담당 EVENT_ADMIN 조회 (참조용)
    Long selectAdminUserIdByFairId(Long fairId);

}
