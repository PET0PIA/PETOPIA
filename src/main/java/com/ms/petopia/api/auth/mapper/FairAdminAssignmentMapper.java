package com.ms.petopia.api.auth.mapper;

import com.ms.petopia.api.auth.domain.FairAdminAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FairAdminAssignmentMapper {

    int insertFairAdminAssignment(FairAdminAssignment assignment);

    /**
     * 이 adminUserId가 이 fairId의 EVENT_ADMIN으로 배정돼 있는지 확인한다. Fair 도메인이
     * 홀/부스 슬롯/운영일 관리 API에서 "로그인한 EVENT_ADMIN이 진짜 이 행사 담당자인지"
     * 검증할 때 쓴다({@code FairAdminAccessGuard} 참고).
     */
    boolean existsByAdminUserIdAndFairId(@Param("adminUserId") Long adminUserId, @Param("fairId") Long fairId);
}
