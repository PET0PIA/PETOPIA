package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.WaitingRoomPolicyRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface WaitingRoomPolicyMapper {

    /** 행사별 대기열 정책. 아직 설정한 적이 없으면 null. */
    WaitingRoomPolicyRow selectByFairId(@Param("fairId") Long fairId);

    int insertPolicy(
            @Param("fairId") Long fairId,
            @Param("enabled") boolean enabled,
            @Param("activeLimit") int activeLimit,
            @Param("updatedBy") Long updatedBy,
            @Param("now") LocalDateTime now
    );

    /**
     * @return 1이면 변경 성공, 0이면 그 사이 다른 관리자가 먼저 바꿨다(버전 불일치)
     */
    int updatePolicy(
            @Param("fairId") Long fairId,
            @Param("enabled") boolean enabled,
            @Param("activeLimit") int activeLimit,
            @Param("updatedBy") Long updatedBy,
            @Param("expectedVersion") int expectedVersion,
            @Param("now") LocalDateTime now
    );
}
