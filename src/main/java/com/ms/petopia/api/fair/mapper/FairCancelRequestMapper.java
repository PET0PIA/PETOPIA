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
     *
     * <p>{@code WHERE}에 {@code status = 'PENDING'}도 함께 건다(조건부 갱신). 두 검토
     * 요청이 동시에 들어와도 하나만 실제로 행을 갱신하게 하기 위한 동시성 방어로,
     * "PENDING인 걸 확인하고 나서 갱신"이 아니라 "갱신 자체가 PENDING일 때만 성공"하게
     * DB 레벨에서 원자적으로 처리한다. 영향받은 행이 0이면 그 사이 이미 다른 검토가
     * 끝났다는 뜻이므로, 호출부(FairCancelRequestService)가 FAIR_CANCEL_REQUEST_NOT_PENDING을
     * 던진다.
     */
    int update(FairCancelRequest fairCancelRequest);
}
