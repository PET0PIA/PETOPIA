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

    /** 관리자 화면에 표시할 운영일별 현장예매 정책을 반환한다. */
    @Transactional(readOnly = true)
    public OnsiteSalesPolicyResponse get(Long fairId, Long fairDateId, Long actorUserId) {
        if (fairId == null || fairId <= 0
                || fairDateId == null || fairDateId <= 0
                || actorUserId == null || actorUserId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        operatorAccessService.assertCanManageFair(fairId, actorUserId);

        FairDateSnapshot fairDate = onsiteReservationMapper.selectFairDate(fairId, fairDateId);
        if (fairDate == null) {
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }
        OnsiteSalesPolicyRow policy = onsiteReservationMapper.selectPolicy(fairDateId);
        if (policy == null) {
            // 아직 설정한 적 없는 운영일. 판매하지 않는 상태(가격 0·마감·정원 제한 없음)를 준다.
            return new OnsiteSalesPolicyResponse(
                    fairId,
                    fairDateId,
                    fairDate.getOperationDate(),
                    0,
                    null,
                    0,
                    OnsiteSalesStatus.CLOSED.name(),
                    0,
                    null
            );
        }
        return new OnsiteSalesPolicyResponse(
                fairId,
                fairDateId,
                fairDate.getOperationDate(),
                policy.getPrice(),
                policy.getCapacity(),
                policy.getReservedCount(),
                policy.getStatus(),
                policy.getVersion(),
                policy.getUpdatedAt()
        );
    }

    /**
     * 운영일별 현장예매 가격·정원·접수 상태를 생성하거나 변경한다.
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
                    request.capacity(),
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
                    request.capacity(),
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
                saved.getCapacity(),
                saved.getReservedCount(),
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
        // 정원은 생략(null)하면 "제한 없음"이다. 값을 줬다면 음수만 막는다.
        // 이미 팔린 수보다 작게 줄이는 것은 막지 않는다 - 판매를 중간에 조일 수 있어야 하고,
        // 이미 발행된 예약을 무효로 만들지도 않는다(새 예매만 매진 처리된다).
        if (request.capacity() != null && request.capacity() < 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

}
