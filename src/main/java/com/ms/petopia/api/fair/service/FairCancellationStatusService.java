package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.FairCancellationStatusResponse;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FairCancellationStatusService {

    private final FairMapper fairMapper;

    /**
     * 정산/결제 도메인이 정산 계산·확정 직전에 호출하는 내부 계약. 판단 로직(취소 여부)은
     * 이 도메인이 갖고, {@code fairs.status}/{@code canceled_at}을 그대로 반영해서 응답한다 -
     * 호출부가 별도로 판단하지 않고 이 응답만 그대로 쓰는 것을 전제로 한다.
     *
     * @throws CommonException {@link ErrorCode#FAIR_NOT_FOUND} 존재하지 않는 행사 ID일 때
     */
    @Transactional(readOnly = true)
    public FairCancellationStatusResponse getCancellationStatus(Long fairId) {
        if (fairId == null || fairId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Fair fair = fairMapper.selectById(fairId);
        if (fair == null) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }
        return new FairCancellationStatusResponse(fairId, fair.getCanceledAt() != null, fair.getCanceledAt());
    }
}
