package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.WaitingTicketResponse;
import com.ms.petopia.api.reservation.service.WaitingRoomPolicyService.WaitingRoomPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 대기열의 계약을 검증한다.
 *
 * <p>중점은 <b>대기열이 정확성 장치가 아니라는 것</b>이다. 꺼져 있거나 Redis가 죽으면
 * 반드시 통과시켜야 한다 — 여기가 무너지면 성능 장치 하나가 전체 예약을 멈춰 세운다.
 */
@ExtendWith(MockitoExtension.class)
class WaitingRoomServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long OTHER_FAIR_ID = 11L;
    private static final Long USER_ID = 20L;
    private static final String TOKEN = "token-abc";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 10, 0);

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private WaitingRoomPolicyService policyService;
    @Mock
    private ReservationTimeProvider timeProvider;

    private WaitingRoomService service;

    @BeforeEach
    void setUp() {
        lenient().when(timeProvider.now()).thenReturn(NOW);
        lenient().when(timeProvider.epochMilli()).thenReturn(1_000_000L);
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        // 대기열을 켠 행사(통과 인원 2명)와 켜지 않은 행사를 각각 준비한다.
        lenient().when(policyService.resolve(FAIR_ID))
                .thenReturn(new WaitingRoomPolicy(true, 2));
        lenient().when(policyService.resolve(OTHER_FAIR_ID))
                .thenReturn(WaitingRoomPolicy.disabled());

        service = new WaitingRoomService(
                redis,
                new WaitingRoomProperties(Duration.ofMinutes(12), Duration.ofMinutes(30), 1000),
                policyService,
                timeProvider
        );
    }

    @Nested
    @DisplayName("대기열을 켜지 않은 행사")
    class Disabled {

        @Test
        @DisplayName("토큰 발급을 요청해도 Redis를 건드리지 않고 통과시킨다")
        void issue_대기열미적용_BYPASSED를반환한다() {
            WaitingTicketResponse response = service.issue(OTHER_FAIR_ID, USER_ID);

            assertThat(response.status()).isEqualTo(WaitingTicketResponse.BYPASSED);
            verify(redis, never()).execute(any(RedisScript.class), any(), any());
        }

        @Test
        @DisplayName("게이트는 토큰 없이도 통과시킨다")
        void admit_대기열미적용_토큰없이통과한다() {
            assertThat(service.admit(OTHER_FAIR_ID, null, USER_ID)).isTrue();
        }
    }

    @Nested
    @DisplayName("Redis 장애")
    class RedisDown {

        /**
         * 이 그룹이 이 테스트 클래스에서 제일 중요하다. 대기열은 유량 조절 장치일 뿐이고
         * 정원을 지키는 것은 DB의 조건부 UPDATE다. Redis가 죽었다고 예약을 막으면
         * 성능 장치가 정합성 장치처럼 행동하는 셈이라 장애 범위만 넓어진다.
         */
        @Test
        @DisplayName("게이트 판정이 실패하면 막지 않고 통과시킨다(fail-open)")
        void admit_Redis예외_통과시킨다() {
            given(valueOperations.get(anyString())).willThrow(new QueryTimeoutException("redis down"));

            assertThat(service.admit(FAIR_ID, TOKEN, USER_ID)).isTrue();
        }

        @Test
        @DisplayName("토큰 발급이 실패하면 대기 없이 통과시킨다")
        void issue_Redis예외_BYPASSED를반환한다() {
            willThrow(new QueryTimeoutException("redis down"))
                    .given(valueOperations).set(anyString(), anyString(), any(Duration.class));

            assertThat(service.issue(FAIR_ID, USER_ID).status())
                    .isEqualTo(WaitingTicketResponse.BYPASSED);
        }

        /** Redis뿐 아니라 정책 저장소가 흔들려도 예약을 막아서는 안 된다. */
        @Test
        @DisplayName("정책 조회가 실패해도 막지 않고 통과시킨다")
        void admit_정책조회예외_통과시킨다() {
            given(policyService.resolve(FAIR_ID)).willThrow(new QueryTimeoutException("db down"));

            assertThat(service.admit(FAIR_ID, TOKEN, USER_ID)).isTrue();
        }

        @Test
        @DisplayName("정책 조회가 실패하면 토큰 발급도 대기 없이 통과시킨다")
        void issue_정책조회예외_BYPASSED를반환한다() {
            given(policyService.resolve(FAIR_ID)).willThrow(new QueryTimeoutException("db down"));

            assertThat(service.issue(FAIR_ID, USER_ID).status())
                    .isEqualTo(WaitingTicketResponse.BYPASSED);
        }
    }

    @Nested
    @DisplayName("토큰 소유자 검증")
    class Ownership {

        /**
         * 이걸 빼면 토큰 하나를 공유해 여러 계정이 게이트를 넘을 수 있어 유입 제한이
         * 통째로 무의미해진다.
         */
        @Test
        @DisplayName("남의 토큰으로는 통과하지 못한다")
        void admit_다른사용자의토큰_거부한다() {
            given(valueOperations.get("waiting:" + FAIR_ID + ":token:" + TOKEN)).willReturn("999");

            assertThat(service.admit(FAIR_ID, TOKEN, USER_ID)).isFalse();
            verify(redis, never()).execute(any(RedisScript.class), any(), any());
        }

        @Test
        @DisplayName("토큰이 없으면 통과하지 못한다")
        void admit_토큰없음_거부한다() {
            assertThat(service.admit(FAIR_ID, null, USER_ID)).isFalse();
            assertThat(service.admit(FAIR_ID, "  ", USER_ID)).isFalse();
        }

        @Test
        @DisplayName("만료돼 사라진 토큰은 통과하지 못한다")
        void admit_만료된토큰_거부한다() {
            given(valueOperations.get(anyString())).willReturn(null);

            assertThat(service.admit(FAIR_ID, TOKEN, USER_ID)).isFalse();
        }

        /**
         * 토큰은 URL에 실려 다니는 값이다. 확인 없이 지우면 남의 토큰 하나로 그 사람의
         * 순번과 활성 슬롯을 날릴 수 있다.
         */
        @Test
        @DisplayName("남의 토큰은 반납해 주지 않는다")
        void leave_다른사용자의토큰_지우지않는다() {
            given(valueOperations.get("waiting:" + FAIR_ID + ":token:" + TOKEN)).willReturn("999");

            service.leave(FAIR_ID, TOKEN, USER_ID);

            verify(redis, never()).execute(any(RedisScript.class), any(), any());
            verify(redis, never()).delete(anyString());
        }

        @Test
        @DisplayName("내 토큰이면 줄과 활성 슬롯에서 모두 지운다")
        void leave_내토큰_반납한다() {
            givenTokenOwner(USER_ID);

            service.leave(FAIR_ID, TOKEN, USER_ID);

            verify(redis).execute(any(RedisScript.class), any(), any());
            verify(redis).delete("waiting:" + FAIR_ID + ":token:" + TOKEN);
        }
    }

    @Nested
    @DisplayName("순번 조회")
    class Status {

        @Test
        @DisplayName("대기 중이면 순번과 앞사람 수를 그대로 돌려준다")
        void status_대기중_순번을반환한다() {
            givenTokenOwner(USER_ID);
            // {상태=0(대기), 순번=5, 앞사람=4, 만료시각=0}
            givenScriptResult(List.of(0L, 5L, 4L, "0"));

            WaitingTicketResponse response = service.status(FAIR_ID, TOKEN, USER_ID);

            assertThat(response.status()).isEqualTo(WaitingTicketResponse.WAITING);
            assertThat(response.position()).isEqualTo(5);
            assertThat(response.ahead()).isEqualTo(4);
            assertThat(response.expiresAt()).isNull();
        }

        @Test
        @DisplayName("승급 이력이 없으면 예상 대기시간을 추정하지 않는다")
        void status_승급이력없음_예상시간은null이다() {
            givenTokenOwner(USER_ID);
            givenScriptResult(List.of(0L, 5L, 4L, "0"));

            // admits 버킷이 비어 있으면 속도를 알 수 없다. 틀린 숫자를 보여주느니 비운다.
            assertThat(service.status(FAIR_ID, TOKEN, USER_ID).estimatedWaitSeconds()).isNull();
        }

        @Test
        @DisplayName("통과했으면 슬롯 만료 시각을 함께 돌려준다")
        void status_통과_만료시각을반환한다() {
            givenTokenOwner(USER_ID);
            // ZSCORE는 문자열로 온다. 만료 시각 = NOW 기준 epoch milli.
            long expiresAt = NOW.plusMinutes(12)
                    .atZone(ReservationTimeProvider.SEOUL_ZONE).toInstant().toEpochMilli();
            givenScriptResult(List.of(1L, 0L, 0L, String.valueOf(expiresAt)));

            WaitingTicketResponse response = service.status(FAIR_ID, TOKEN, USER_ID);

            assertThat(response.status()).isEqualTo(WaitingTicketResponse.ADMITTED);
            assertThat(response.expiresAt()).isEqualTo(NOW.plusMinutes(12));
        }

        @Test
        @DisplayName("서버가 모르는 토큰이면 대기 상태로 돌려보내 재발급을 유도한다")
        void status_모르는토큰_대기상태로반환한다() {
            givenTokenOwner(USER_ID);
            givenScriptResult(List.of(-1L, 0L, 0L, "0"));

            WaitingTicketResponse response = service.status(FAIR_ID, TOKEN, USER_ID);

            assertThat(response.status()).isEqualTo(WaitingTicketResponse.WAITING);
            assertThat(response.position()).isZero();
        }
    }

    private void givenTokenOwner(Long userId) {
        given(valueOperations.get("waiting:" + FAIR_ID + ":token:" + TOKEN))
                .willReturn(String.valueOf(userId));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void givenScriptResult(List<Object> result) {
        given(redis.execute(any(RedisScript.class), any(List.class),
                any(), any(), any(), any(), any(), any()))
                .willReturn(result);
    }
}
