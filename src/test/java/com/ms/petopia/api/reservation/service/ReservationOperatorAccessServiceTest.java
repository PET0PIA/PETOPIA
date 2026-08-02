package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationUserSnapshot;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationOperatorAccessServiceTest {

    @Mock
    private ReservationMapper mapper;
    @InjectMocks
    private ReservationOperatorAccessService service;

    @Test
    void superAdminCanManageAnyFairWithoutAssignment() {
        given(mapper.selectUserSnapshot(20L)).willReturn(user("SUPER_ADMIN"));

        service.assertCanManageFair(10L, 20L);

        verify(mapper, never()).isAssignedEventAdmin(10L, 20L);
    }

    @Test
    void assignedEventAdminCanManageFair() {
        given(mapper.selectUserSnapshot(20L)).willReturn(user("EVENT_ADMIN"));
        given(mapper.isAssignedEventAdmin(10L, 20L)).willReturn(true);

        service.assertCanManageFair(10L, 20L);

        verify(mapper).isAssignedEventAdmin(10L, 20L);
    }

    @Test
    void unassignedEventAdminIsRejected() {
        given(mapper.selectUserSnapshot(20L)).willReturn(user("EVENT_ADMIN"));

        assertThatThrownBy(() -> service.assertCanManageFair(10L, 20L))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    private ReservationUserSnapshot user(String role) {
        ReservationUserSnapshot user = new ReservationUserSnapshot();
        user.setUserId(20L);
        user.setRole(role);
        user.setStatus("ACTIVE");
        return user;
    }
}
