package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.UpdateWaitingRoomPolicyRequest;
import com.ms.petopia.api.reservation.dto.WaitingRoomPolicyResponse;
import com.ms.petopia.api.reservation.dto.WaitingRoomPolicyRow;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.api.reservation.mapper.WaitingRoomPolicyMapper;
import com.ms.petopia.api.reservation.service.WaitingRoomPolicyService.WaitingRoomPolicy;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WaitingRoomPolicyServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long ADMIN_ID = 99L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 10, 0);

    @Mock
    private WaitingRoomPolicyMapper policyMapper;
    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private ReservationOperatorAccessService operatorAccessService;
    @Mock
    private ReservationTimeProvider timeProvider;

    private WaitingRoomPolicyService service;

    @BeforeEach
    void setUp() {
        lenient().when(timeProvider.now()).thenReturn(NOW);
        service = new WaitingRoomPolicyService(
                policyMapper,
                reservationMapper,
                operatorAccessService,
                new WaitingRoomProperties(Duration.ofMinutes(12), Duration.ofMinutes(30), 1000),
                timeProvider
        );
    }

    @Test
    @DisplayName("설정한 적 없는 행사는 대기열을 적용하지 않는다")
    void resolve_정책없음_비활성이다() {
        given(policyMapper.selectByFairId(FAIR_ID)).willReturn(null);

        assertThat(service.resolve(FAIR_ID).enabled()).isFalse();
    }

    @Test
    @DisplayName("enabled=false로 저장된 행사도 적용하지 않는다")
    void resolve_꺼짐_비활성이다() {
        given(policyMapper.selectByFairId(FAIR_ID)).willReturn(row(false, 500, 3));

        assertThat(service.resolve(FAIR_ID).enabled()).isFalse();
    }

    @Test
    @DisplayName("켜져 있으면 저장된 통과 인원을 그대로 쓴다")
    void resolve_켜짐_통과인원을반환한다() {
        given(policyMapper.selectByFairId(FAIR_ID)).willReturn(row(true, 500, 3));

        WaitingRoomPolicy policy = service.resolve(FAIR_ID);

        assertThat(policy.enabled()).isTrue();
        assertThat(policy.activeLimit()).isEqualTo(500);
    }

    /**
     * 게이트는 예약 요청마다 이걸 부른다. 매번 DB를 때리면 오픈 러시를 막으려고 만든 장치가
     * 스스로 DB 부하를 만드는 셈이 된다.
     */
    @Test
    @DisplayName("연속 조회는 DB를 한 번만 읽는다")
    void resolve_반복호출_DB조회는한번이다() {
        given(policyMapper.selectByFairId(FAIR_ID)).willReturn(row(true, 500, 3));

        for (int i = 0; i < 50; i++) {
            service.resolve(FAIR_ID);
        }

        verify(policyMapper, times(1)).selectByFairId(FAIR_ID);
    }

    /**
     * DB가 흔들리면 예약 자체가 이미 위태롭다. 대기열까지 막아 장애를 하나 더 얹지 않는다.
     */
    @Test
    @DisplayName("정책 조회에 실패하면 대기열 없이 진행한다")
    void resolve_DB예외_비활성으로떨어진다() {
        given(policyMapper.selectByFairId(FAIR_ID)).willThrow(new QueryTimeoutException("db down"));

        assertThat(service.resolve(FAIR_ID).enabled()).isFalse();
    }

    @Test
    @DisplayName("저장하면 이 인스턴스의 캐시는 즉시 새 값을 본다")
    void save_저장후_캐시가갱신된다() {
        given(reservationMapper.existsFair(FAIR_ID)).willReturn(true);
        given(policyMapper.selectByFairId(FAIR_ID))
                .willReturn(null)                    // resolve() 최초 조회 - 아직 정책 없음
                .willReturn(null)                    // save() 안의 현재값 조회
                .willReturn(row(true, 300, 0));      // save() 뒤 재조회 + 이후 resolve()

        assertThat(service.resolve(FAIR_ID).enabled()).isFalse();

        service.save(FAIR_ID, ADMIN_ID, new UpdateWaitingRoomPolicyRequest(true, 300, 0));

        WaitingRoomPolicy after = service.resolve(FAIR_ID);
        assertThat(after.enabled()).isTrue();
        assertThat(after.activeLimit()).isEqualTo(300);
    }

    @Test
    @DisplayName("정책이 없는데 expectedVersion이 0이 아니면 충돌로 거절한다")
    void save_정책없음_버전불일치면충돌이다() {
        given(reservationMapper.existsFair(FAIR_ID)).willReturn(true);
        given(policyMapper.selectByFairId(FAIR_ID)).willReturn(null);

        assertErrorCode(
                () -> service.save(FAIR_ID, ADMIN_ID, new UpdateWaitingRoomPolicyRequest(true, 300, 5)),
                ErrorCode.WAITING_ROOM_POLICY_CONFLICT
        );
        verify(policyMapper, never()).insertPolicy(anyLong(), anyBoolean(), anyInt(), anyLong(), any());
    }

    /** 생략을 허용하면 조회 없이 쓴 요청이 낙관적 잠금을 우회한다. 최초 생성도 예외가 아니다. */
    @Test
    @DisplayName("정책이 없는데 expectedVersion을 생략하면 충돌로 거절한다")
    void save_정책없음_버전생략이면충돌이다() {
        given(reservationMapper.existsFair(FAIR_ID)).willReturn(true);
        given(policyMapper.selectByFairId(FAIR_ID)).willReturn(null);

        assertErrorCode(
                () -> service.save(FAIR_ID, ADMIN_ID, new UpdateWaitingRoomPolicyRequest(true, 300, null)),
                ErrorCode.WAITING_ROOM_POLICY_CONFLICT
        );
        verify(policyMapper, never()).insertPolicy(anyLong(), anyBoolean(), anyInt(), anyLong(), any());
    }

    /**
     * 최초 생성은 "조회했더니 없어서 INSERT"라 조회와 쓰기가 갈라져 있다. version 조건이
     * 지켜주는 UPDATE 경로와 달리 이 구간에만 창이 남는데, UK가 그 창을 막는다.
     * 사용자에게는 동시 수정과 같은 상황이므로 500이 아니라 같은 충돌로 나가야 한다.
     */
    @Test
    @DisplayName("최초 생성이 겹치면 UK가 막고 충돌로 돌려보낸다")
    void save_동시생성_충돌이다() {
        given(reservationMapper.existsFair(FAIR_ID)).willReturn(true);
        given(policyMapper.selectByFairId(FAIR_ID)).willReturn(null);
        given(policyMapper.insertPolicy(FAIR_ID, true, 300, ADMIN_ID, NOW))
                .willThrow(new DuplicateKeyException("UK_WAITING_ROOM_FAIR"));

        assertErrorCode(
                () -> service.save(FAIR_ID, ADMIN_ID, new UpdateWaitingRoomPolicyRequest(true, 300, 0)),
                ErrorCode.WAITING_ROOM_POLICY_CONFLICT
        );
    }

    /**
     * 오픈 중에 두 관리자가 통과 인원을 각자 조정하면, 나중에 쓴 쪽이 상대의 변경을 모르고
     * 덮어쓴다. 유입 상한이 조용히 뒤바뀌는 건 지표를 보며 조정하는 작업에서 치명적이다.
     */
    @Test
    @DisplayName("다른 관리자가 먼저 바꿨으면 덮어쓰지 않고 충돌로 거절한다")
    void save_동시수정_충돌이다() {
        given(reservationMapper.existsFair(FAIR_ID)).willReturn(true);
        given(policyMapper.selectByFairId(FAIR_ID)).willReturn(row(true, 500, 3));
        given(policyMapper.updatePolicy(FAIR_ID, true, 300, ADMIN_ID, 3, NOW)).willReturn(0);

        assertErrorCode(
                () -> service.save(FAIR_ID, ADMIN_ID, new UpdateWaitingRoomPolicyRequest(true, 300, 3)),
                ErrorCode.WAITING_ROOM_POLICY_CONFLICT
        );
    }

    /** 0 이하면 아무도 통과하지 못해 예약이 완전히 멈춘다. */
    @Test
    @DisplayName("통과 인원이 1 미만이거나 상한을 넘으면 거절한다")
    void save_통과인원범위밖_거절한다() {
        assertErrorCode(
                () -> service.save(FAIR_ID, ADMIN_ID, new UpdateWaitingRoomPolicyRequest(true, 0, 0)),
                ErrorCode.INVALID_INPUT_VALUE
        );
        assertErrorCode(
                () -> service.save(FAIR_ID, ADMIN_ID, new UpdateWaitingRoomPolicyRequest(true, 100_001, 0)),
                ErrorCode.INVALID_INPUT_VALUE
        );
    }

    @Test
    @DisplayName("담당하지 않는 행사는 조회도 변경도 할 수 없다")
    void 권한없는관리자_거절한다() {
        org.mockito.BDDMockito.willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(operatorAccessService).assertCanManageFair(FAIR_ID, ADMIN_ID);

        assertErrorCode(() -> service.get(FAIR_ID, ADMIN_ID), ErrorCode.ACCESS_DENIED);
        assertErrorCode(
                () -> service.save(FAIR_ID, ADMIN_ID, new UpdateWaitingRoomPolicyRequest(true, 300, 0)),
                ErrorCode.ACCESS_DENIED
        );
    }

    @Test
    @DisplayName("설정한 적 없는 행사를 조회하면 기본값과 version 0을 알려준다")
    void get_정책없음_기본값을반환한다() {
        given(reservationMapper.existsFair(FAIR_ID)).willReturn(true);
        given(policyMapper.selectByFairId(FAIR_ID)).willReturn(null);

        WaitingRoomPolicyResponse response = service.get(FAIR_ID, ADMIN_ID);

        assertThat(response.enabled()).isFalse();
        assertThat(response.activeLimit()).isEqualTo(1000);
        assertThat(response.version()).isZero();
        assertThat(response.updatedAt()).isNull();
    }

    private WaitingRoomPolicyRow row(boolean enabled, int activeLimit, int version) {
        WaitingRoomPolicyRow row = new WaitingRoomPolicyRow();
        row.setFairId(FAIR_ID);
        row.setEnabled(enabled);
        row.setActiveLimit(activeLimit);
        row.setVersion(version);
        row.setUpdatedAt(NOW);
        return row;
    }

    private void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
