package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.WaitingTicketResponse;
import com.ms.petopia.api.reservation.service.WaitingRoomPolicyService.WaitingRoomPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 오픈 직후 유입 자체를 제한하는 대기열(Waiting Room).
 *
 * <p><b>이 계층은 정확성에 아무 책임이 없다.</b> 정원을 지키는 것은 오직
 * {@link ReservationCapacityMapper#occupy}의 조건부 UPDATE다. 대기열은 그 아래 계층이
 * 감당할 수 있는 만큼만 요청을 흘려보내는 유량 조절 장치일 뿐이다.
 *
 * <p>그래서 <b>Redis가 죽으면 전부 통과시킨다</b>(fail-open). 대기열이 멈췄다고 예약을
 * 막으면, 성능 장치가 정합성 장치처럼 행동하는 셈이라 장애 범위가 넓어진다. 대기열 없이
 * 느리게라도 예약이 되는 편이 낫다.
 *
 * <h2>자료구조</h2>
 * <table>
 *   <caption>행사별 Redis 키</caption>
 *   <tr><th>키</th><th>타입</th><th>내용</th></tr>
 *   <tr><td>{@code waiting:{fairId}:queue}</td><td>ZSET</td>
 *       <td>member=토큰, score=줄 선 시각(ms). 순번은 ZRANK로 얻는다</td></tr>
 *   <tr><td>{@code waiting:{fairId}:active}</td><td>ZSET</td>
 *       <td>member=토큰, score=슬롯 만료 시각(ms). 통과한 사람들</td></tr>
 *   <tr><td>{@code waiting:{fairId}:token:{token}}</td><td>STRING</td>
 *       <td>userId. 토큰을 발급받은 본인인지 검증한다</td></tr>
 *   <tr><td>{@code waiting:{fairId}:admits:{분}}</td><td>STRING</td>
 *       <td>그 분에 승급한 인원. 예상 대기시간 추정에만 쓴다</td></tr>
 * </table>
 *
 * <h2>승급 시점</h2>
 * 별도 스케줄러를 두지 않는다. <b>폴링 요청이 들어올 때마다</b> 승급을 함께 처리한다.
 * 아무도 기다리지 않으면 승급할 이유도 없고, 스케줄러는 인스턴스 수만큼 중복 실행되는
 * 문제를 다시 불러온다.
 *
 * <h2>설정</h2>
 * 대기열을 켤지와 통과 인원은 {@link WaitingRoomPolicyService}가 DB에서 읽어온다. 오픈 직후
 * 지표를 보며 조정해야 하는 값이라 재기동 없이 바뀌어야 하기 때문이다. 슬롯·토큰 TTL만
 * 프로퍼티에 남아 있다 — 결제 제한시간과 맞물려 있어 행사별로 다를 이유가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingRoomService {

    /** 승급 인원 집계 버킷의 수명. 2분치를 읽으므로 그보다 넉넉히 잡는다. */
    private static final long ADMIT_BUCKET_TTL_SECONDS = 180;

    /** 예상 대기시간을 추정할 때 승급 속도를 계산하는 창(초). 버킷 2개(현재 분 + 직전 분). */
    private static final long ADMIT_RATE_WINDOW_SECONDS = 120;

    /**
     * 만료 슬롯 회수 → 신규 등록 → 빈 슬롯만큼 승급 → 이 토큰 상태 조회를 한 번에 한다.
     *
     * <p>여러 명령을 나눠 보내면 그 사이에 다른 인스턴스가 끼어들어 같은 슬롯을 두 명에게
     * 주거나(활성 정원 초과) 순번이 뒤집힌다. Lua 한 덩어리로 보내 원자성을 확보한다.
     *
     * <p>반환: {@code {상태, 순번, 앞사람수, 만료시각ms}}.
     * 상태는 1=통과, 0=대기, -1=모르는 토큰(만료됐거나 다른 행사의 토큰).
     */
    private static final RedisScript<List> PROMOTE_AND_STATUS = new DefaultRedisScript<>("""
            local queue, active, admits = KEYS[1], KEYS[2], KEYS[3]
            local now     = tonumber(ARGV[1])
            local limit   = tonumber(ARGV[2])
            local ttl     = tonumber(ARGV[3])
            local token   = ARGV[4]
            local enqueue = tonumber(ARGV[5])

            -- 만료된 슬롯을 회수한다. 이걸 먼저 해야 빈 자리가 정확히 계산된다.
            redis.call('ZREMRANGEBYSCORE', active, '-inf', now)

            -- 신규 발급. 이미 통과한 토큰이면 다시 줄 세우지 않는다.
            -- ZADD NX라서 이미 줄 서 있던 토큰의 순번도 그대로 유지된다(재발급 멱등).
            if enqueue == 1 and redis.call('ZSCORE', active, token) == false then
                redis.call('ZADD', queue, 'NX', now, token)
            end

            -- 빈 슬롯만큼 앞에서부터 승급
            local free = limit - redis.call('ZCARD', active)
            local promoted = 0
            while free > 0 do
                local head = redis.call('ZPOPMIN', queue)
                if not head or #head == 0 then break end
                redis.call('ZADD', active, now + ttl, head[1])
                promoted = promoted + 1
                free = free - 1
            end
            if promoted > 0 then
                redis.call('INCRBY', admits, promoted)
                redis.call('EXPIRE', admits, ARGV[6])
            end

            -- 이 토큰의 현재 상태
            local expireAt = redis.call('ZSCORE', active, token)
            if expireAt then
                return {1, 0, 0, expireAt}
            end
            local rank = redis.call('ZRANK', queue, token)
            if rank then
                return {0, rank + 1, rank, '0'}
            end
            return {-1, 0, 0, '0'}
            """, List.class);

    /**
     * 보호 대상 API를 통과할 때 슬롯 수명을 연장한다(sliding).
     *
     * <p>폴링에서는 연장하지 않는다. 연장까지 해버리면 대기 화면을 열어둔 채 아무것도 하지
     * 않는 사용자가 슬롯을 영원히 쥐고, 뒷사람이 못 들어온다.
     *
     * @return 1이면 유효한 활성 토큰, 0이면 만료됐거나 활성 슬롯이 아님
     */
    private static final RedisScript<Long> TOUCH_ACTIVE = new DefaultRedisScript<>("""
            local active = KEYS[1]
            local now, ttl, token = tonumber(ARGV[1]), tonumber(ARGV[2]), ARGV[3]
            redis.call('ZREMRANGEBYSCORE', active, '-inf', now)
            if redis.call('ZSCORE', active, token) == false then
                return 0
            end
            redis.call('ZADD', active, now + ttl, token)
            return 1
            """, Long.class);

    private static final RedisScript<Long> LEAVE = new DefaultRedisScript<>("""
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final WaitingRoomProperties properties;
    private final WaitingRoomPolicyService policyService;
    private final ReservationTimeProvider timeProvider;

    /**
     * 대기 토큰을 발급한다. 빈 슬롯이 있으면 발급과 동시에 통과(ADMITTED)될 수 있다.
     *
     * <p>같은 사용자가 여러 번 호출하면 그때마다 새 토큰이 나온다. 토큰을 잃어버린
     * 사용자가 다시 줄을 설 수 있어야 하기 때문인데, 대신 맨 뒤로 간다. 프론트는 토큰을
     * sessionStorage에 보관해 새로고침으로 순번을 잃지 않게 한다.
     */
    public WaitingTicketResponse issue(Long fairId, Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            WaitingRoomPolicy policy = policyService.resolve(fairId);
            if (!policy.enabled()) {
                return WaitingTicketResponse.bypassed();
            }
            redis.opsForValue().set(tokenKey(fairId, token), String.valueOf(userId), properties.ticketTtl());
            return promoteAndDescribe(fairId, policy, token, true);
        } catch (RuntimeException e) {
            log.warn("대기열 토큰 발급 실패. Redis 장애로 간주하고 통과시킨다. fairId={}", fairId, e);
            return WaitingTicketResponse.bypassed();
        }
    }

    /**
     * 순번을 조회하면서 승급도 함께 처리한다.
     *
     * @throws com.ms.petopia.global.exception.CommonException 토큰이 이 사용자의 것이 아닐 때
     */
    public WaitingTicketResponse status(Long fairId, String token, Long userId) {
        try {
            WaitingRoomPolicy policy = policyService.resolve(fairId);
            if (!policy.enabled()) {
                return WaitingTicketResponse.bypassed();
            }
            if (!isTokenOwnedBy(fairId, token, userId)) {
                // 만료됐거나 남의 토큰. 새로 발급받아 다시 줄을 서야 한다.
                return new WaitingTicketResponse(token, WaitingTicketResponse.WAITING, 0, 0, null, null);
            }
            return promoteAndDescribe(fairId, policy, token, false);
        } catch (RuntimeException e) {
            log.warn("대기열 순번 조회 실패. Redis 장애로 간주하고 통과시킨다. fairId={}", fairId, e);
            return WaitingTicketResponse.bypassed();
        }
    }

    /**
     * 사용자가 대기를 포기했을 때 슬롯을 즉시 반납한다. TTL을 기다리지 않아 뒷사람이 빨리 들어온다.
     *
     * <p>토큰 소유자만 반납할 수 있다. 토큰은 URL에 실려 다니는 값이라, 확인 없이 지우면
     * 남의 토큰 하나로 그 사람의 순번과 활성 슬롯을 날릴 수 있다.
     */
    public void leave(Long fairId, String token, Long userId) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            if (!policyService.resolve(fairId).enabled()) {
                return;
            }
            if (!isTokenOwnedBy(fairId, token, userId)) {
                // 남의 토큰이거나 이미 만료됐다. 어느 쪽이든 지울 것이 없다.
                return;
            }
            redis.execute(LEAVE, List.of(queueKey(fairId), activeKey(fairId)), token);
            redis.delete(tokenKey(fairId, token));
        } catch (RuntimeException e) {
            log.warn("대기열 이탈 처리 실패. 슬롯은 TTL로 회수된다. fairId={}", fairId, e);
        }
    }

    /**
     * 보호 대상 API 호출을 허용할지 판정하고, 허용되면 슬롯 수명을 연장한다.
     *
     * <p>Redis 장애 시 {@code true}를 반환한다(fail-open).
     */
    public boolean admit(Long fairId, String token, Long userId) {
        try {
            // 정책 조회도 try 안에 둔다. DB가 흔들려 여기서 예외가 나면 대기열이 예약을
            // 막아버리는데, 그건 이 계층이 절대 하면 안 되는 일이다.
            if (!policyService.resolve(fairId).enabled()) {
                return true;
            }
            if (token == null || token.isBlank()) {
                return false;
            }
            if (!isTokenOwnedBy(fairId, token, userId)) {
                return false;
            }
            Long result = redis.execute(
                    TOUCH_ACTIVE,
                    List.of(activeKey(fairId)),
                    String.valueOf(timeProvider.epochMilli()),
                    String.valueOf(properties.activeTtl().toMillis()),
                    token
            );
            return result != null && result == 1L;
        } catch (RuntimeException e) {
            log.warn("대기열 통과 판정 실패. Redis 장애로 간주하고 통과시킨다. fairId={}", fairId, e);
            return true;
        }
    }

    private WaitingTicketResponse promoteAndDescribe(
            Long fairId, WaitingRoomPolicy policy, String token, boolean enqueue) {
        long now = timeProvider.epochMilli();
        List<?> raw = redis.execute(
                PROMOTE_AND_STATUS,
                List.of(queueKey(fairId), activeKey(fairId), admitBucketKey(fairId, now)),
                String.valueOf(now),
                String.valueOf(policy.activeLimit()),
                String.valueOf(properties.activeTtl().toMillis()),
                token,
                enqueue ? "1" : "0",
                String.valueOf(ADMIT_BUCKET_TTL_SECONDS)
        );
        if (raw == null || raw.size() < 4) {
            // 스크립트가 기대한 모양을 반환하지 않았다. 막기보다 통과시킨다.
            log.warn("대기열 스크립트 응답이 예상과 다르다. fairId={}, raw={}", fairId, raw);
            return WaitingTicketResponse.bypassed();
        }

        long state = toLong(raw.get(0));
        if (state == 1L) {
            return new WaitingTicketResponse(
                    token,
                    WaitingTicketResponse.ADMITTED,
                    0,
                    0,
                    0L,
                    toDateTime(toLong(raw.get(3)))
            );
        }
        if (state < 0L) {
            // 모르는 토큰. 프론트가 재발급하도록 대기 상태로 돌려보낸다.
            return new WaitingTicketResponse(token, WaitingTicketResponse.WAITING, 0, 0, null, null);
        }

        long position = toLong(raw.get(1));
        long ahead = toLong(raw.get(2));
        return new WaitingTicketResponse(
                token,
                WaitingTicketResponse.WAITING,
                position,
                ahead,
                estimateWaitSeconds(fairId, now, ahead),
                null
        );
    }

    /**
     * 최근 승급 속도로 남은 대기 시간을 추정한다.
     *
     * <p>정확할 필요는 없다. 다만 <b>들쭉날쭉하면 안 된다</b> — 숫자가 늘었다 줄었다 하면
     * 사용자가 새로고침을 반복해서 대기열 자체가 새로운 부하가 된다. 그래서 순간값이 아니라
     * 2분 창의 평균을 쓰고, 단조 감소 보정은 직전 값을 들고 있는 프론트에서 한다.
     *
     * @return 추정치. 아직 승급 이력이 없어 속도를 알 수 없으면 null
     */
    private Long estimateWaitSeconds(Long fairId, long now, long ahead) {
        long admitted = readAdmits(fairId, now) + readAdmits(fairId, now - 60_000);
        if (admitted <= 0) {
            return null;
        }
        double ratePerSecond = (double) admitted / ADMIT_RATE_WINDOW_SECONDS;
        return (long) Math.ceil(ahead / ratePerSecond);
    }

    private long readAdmits(Long fairId, long epochMilli) {
        String value = redis.opsForValue().get(admitBucketKey(fairId, epochMilli));
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isTokenOwnedBy(Long fairId, String token, Long userId) {
        if (token == null || token.isBlank() || userId == null) {
            return false;
        }
        String owner = redis.opsForValue().get(tokenKey(fairId, token));
        return String.valueOf(userId).equals(owner);
    }

    private LocalDateTime toDateTime(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ReservationTimeProvider.SEOUL_ZONE);
    }

    /** Lua 반환값은 Long으로 오지만 ZSCORE는 문자열이라 두 경우를 모두 받는다. */
    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return (long) Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String queueKey(Long fairId) {
        return "waiting:" + fairId + ":queue";
    }

    private String activeKey(Long fairId) {
        return "waiting:" + fairId + ":active";
    }

    private String tokenKey(Long fairId, String token) {
        return "waiting:" + fairId + ":token:" + token;
    }

    private String admitBucketKey(Long fairId, long epochMilli) {
        return "waiting:" + fairId + ":admits:" + (epochMilli / 60_000);
    }
}
