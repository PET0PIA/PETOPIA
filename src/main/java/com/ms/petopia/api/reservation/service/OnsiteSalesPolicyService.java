package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.FairDateSnapshot;
import com.ms.petopia.api.reservation.dto.OnsiteSalesPolicyResponse;
import com.ms.petopia.api.reservation.dto.OnsiteSalesPolicyRow;
import com.ms.petopia.api.reservation.dto.UpdateOnsiteSalesPolicyRequest;
import com.ms.petopia.api.reservation.mapper.OnsiteReservationMapper;
import com.ms.petopia.api.reservation.model.OnsiteSalesStatus;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OnsiteSalesPolicyService {

    private final OnsiteReservationMapper onsiteReservationMapper;
    private final ReservationOperatorAccessService operatorAccessService;
    private final ReservationTimeProvider timeProvider;

    /**
     * 운영일별 현장예매 가격과 접수 상태를 생성하거나 변경한다.
     * EVENT_ADMIN은 배정된 행사만, SUPER_ADMIN은 모든 행사를 변경할 수 있다.
     */
    @Transactional
    public OnsiteSalesPolicyResponse save(
            Long fairId,
            Long fairDateId,
            Long actorUserId,
            UpdateOnsiteSalesPolicyRequest request
    ) {
        validateRequest(fairId, fairDateId, actorUserId, request);
        OnsiteSalesStatus status = OnsiteSalesStatus.from(request.status())
                .orElseThrow(() -> new CommonException(ErrorCode.INVALID_INPUT_VALUE));

        operatorAccessService.assertCanManageFair(fairId, actorUserId);

        FairDateSnapshot fairDate = onsiteReservationMapper.selectFairDateForUpdate(fairId, fairDateId);
        if (fairDate == null) {
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }

        LocalDateTime now = timeProvider.now();
        OnsiteSalesPolicyRow current = onsiteReservationMapper.selectPolicy(fairDateId);
        if (current == null) {
            if (request.expectedVersion() != null && request.expectedVersion() != 0) {
                throw new CommonException(ErrorCode.ONSITE_SALES_POLICY_CONFLICT);
            }
            onsiteReservationMapper.insertPolicy(
                    fairDateId,
                    request.price(),
                    status.name(),
                    actorUserId,
                    now
            );
        } else {
            if (request.expectedVersion() == null) {
                throw new CommonException(ErrorCode.ONSITE_SALES_POLICY_CONFLICT);
            }
            int updated = onsiteReservationMapper.updatePolicy(
                    fairDateId,
                    request.price(),
                    status.name(),
                    actorUserId,
                    request.expectedVersion(),
                    now
            );
            if (updated != 1) {
                throw new CommonException(ErrorCode.ONSITE_SALES_POLICY_CONFLICT);
            }
        }

        OnsiteSalesPolicyRow saved = onsiteReservationMapper.selectPolicy(fairDateId);
        return new OnsiteSalesPolicyResponse(
                fairId,
                fairDateId,
                fairDate.getOperationDate(),
                saved.getPrice(),
                saved.getStatus(),
                saved.getVersion(),
                saved.getUpdatedAt()
        );
    }

    private void validateRequest(
            Long fairId,
            Long fairDateId,
            Long actorUserId,
            UpdateOnsiteSalesPolicyRequest request
    ) {
        if (fairId == null || fairId <= 0
                || fairDateId == null || fairDateId <= 0
                || actorUserId == null || actorUserId <= 0
                || request == null
                || request.price() == null || request.price() < 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

}
