package com.ms.petopia.api.auth.mapper;

import com.ms.petopia.api.auth.domain.FairAdminAssignment;
import com.ms.petopia.api.fair.dto.AssignedFairSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FairAdminAssignmentMapper {

    int insertFairAdminAssignment(FairAdminAssignment assignment);

    /**
     * 이 adminUserId가 이 fairId의 EVENT_ADMIN으로 배정돼 있는지 확인한다. Fair 도메인이
     * 홀/부스 슬롯/운영일 관리 API에서 "로그인한 EVENT_ADMIN이 진짜 이 행사 담당자인지"
     * 검증할 때 쓴다({@code FairAdminAccessGuard} 참고).
     */
    boolean existsByAdminUserIdAndFairId(@Param("adminUserId") Long adminUserId, @Param("fairId") Long fairId);

    /** 이 adminUserId에게 배정된 행사 목록(fairId + name)을 최신 행사 순으로 반환한다. */
    List<AssignedFairSummary> selectByAdminUserId(@Param("adminUserId") Long adminUserId);
}
