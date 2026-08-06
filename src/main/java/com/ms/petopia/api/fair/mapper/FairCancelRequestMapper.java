package com.ms.petopia.api.fair.mapper;

import com.ms.petopia.api.fair.dto.FairCancelRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * fair_cancel_requests 테이블 매퍼. XML은 {@code mapper/fair/FairCancelRequestMapper.xml}에 있다.
 */
@Mapper
public interface FairCancelRequestMapper {

    int insert(FairCancelRequest fairCancelRequest);

    FairCancelRequest selectById(@Param("fairCancelRequestId") Long fairCancelRequestId);

    /** 특정 행사의 취소 신청 이력을 최신순으로 조회한다. */
    List<FairCancelRequest> selectByFairId(@Param("fairId") Long fairId);

    /**
     * 같은 행사에 이미 검토 대기 중인(PENDING) 취소 신청이 있는지 확인한다(중복 신청 방지용).
     * 없으면 null.
     */
    FairCancelRequest selectPendingByFairId(@Param("fairId") Long fairId);

    /**
     * null이 아닌 필드만 갱신한다. 검토(승인/반려) 시 채우는 필드가 달라서 범용으로 둔다.
     * fair_id/requested_by/reason/created_at은 갱신 대상이 아니다.
     */
    int update(FairCancelRequest fairCancelRequest);
}
