package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationUserSnapshot;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationOperatorAccessService {

    private final ReservationMapper reservationMapper;

    public void assertCanManageFair(Long fairId, Long actorUserId) {
        if (fairId == null || fairId <= 0 || actorUserId == null || actorUserId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        ReservationUserSnapshot actor = reservationMapper.selectUserSnapshot(actorUserId);
        if (actor == null) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
        if (!"ACTIVE".equals(actor.getStatus())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
        if ("SUPER_ADMIN".equals(actor.getRole())) {
            return;
        }
        if (!"EVENT_ADMIN".equals(actor.getRole())
                || !reservationMapper.isAssignedEventAdmin(fairId, actorUserId)) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
    }
}
