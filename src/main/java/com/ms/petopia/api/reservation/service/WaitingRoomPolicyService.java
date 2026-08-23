package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.UpdateWaitingRoomPolicyRequest;
import com.ms.petopia.api.reservation.dto.WaitingRoomPolicyResponse;
import com.ms.petopia.api.reservation.dto.WaitingRoomPolicyRow;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.api.reservation.mapper.WaitingRoomPolicyMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 행사별 대기열 정책을 읽고 쓴다.
 *
 * <p>이 값들은 오픈 직후 지표를 보며 조정해야 하는 성격이라 DB에 둔다. 프로퍼티로 두면
 * 정작 손대야 하는 순간에 재배포나 재기동을 요구한다.
 *
 * <h2>왜 캐시가 필요한가</h2>
 * {@link #resolve}는 <b>예약 생성 요청마다</b> 호출된다. 오픈 러시를 막으려고 만든 게이트가
 * 매 요청 DB를 한 번씩 더 때리면 앞뒤가 맞지 않는다. 그래서 짧은 TTL 캐시를 두고,
 * 그 대가로 <b>설정 변경이 최대 {@value #CACHE_TTL_SECONDS}초 늦게 반영</b>되는 것을 받아들인다.
 * 통과 인원을 조정하는 작업에서 이 정도 지연은 문제가 되지 않는다.
 *
 * <p>캐시는 인스턴스 로컬이다. 저장한 인스턴스는 즉시 반영되지만 나머지는 TTL만큼 뒤에
 * 따라온다. 대기열은 정확성 장치가 아니라 유량 조절 장치라 인스턴스 간에 잠깐 값이
 * 달라도 무해하다 — 정원은 어차피 DB 조건부 UPDATE가 지킨다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingRoomPolicyService {

    private static final long CACHE_TTL_SECONDS = 10;
    private static final Duration CACHE_TTL = Duration.ofSeconds(CACHE_TTL_SECONDS);

    private final WaitingRoomPolicyMapper policyMapper;
    private final ReservationMapper reservationMapper;
    private final ReservationOperatorAccessService operatorAccessService;
    private final WaitingRoomProperties properties;
    private final ReservationTimeProvider timeProvider;

    private final ConcurrentHashMap<Long, CachedPolicy> cache = new ConcurrentHashMap<>();

    /** 게이트가 쓰는 값. 설정한 적이 없거나 조회에 실패하면 "대기열 없음"으로 본다. */
    public WaitingRoomPolicy resolve(Long fairId) {
        if (fairId == null || fairId <= 0) {
            return WaitingRoomPolicy.disabled();
        }
        CachedPolicy cached = cache.get(fairId);
        LocalDateTime now = timeProvider.now();
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.policy();
        }

        WaitingRoomPolicy policy = loadPolicy(fairId);
        cache.put(fairId, new CachedPolicy(policy, now.plus(CACHE_TTL)));
        return policy;
    }

    private WaitingRoomPolicy loadPolicy(Long fairId) {
        try {
            WaitingRoomPolicyRow row = policyMapper.selectByFairId(fairId);
            if (row == null || !row.isEnabled()) {
                return WaitingRoomPolicy.disabled();
            }
            return new WaitingRoomPolicy(true, row.getActiveLimit());
        } catch (RuntimeException e) {
            // DB가 흔들리면 예약 자체가 이미 위태롭다. 여기서 대기열까지 막아 장애를
            // 하나 더 얹지 않는다 — 대기열은 성능 장치일 뿐이다.
            log.warn("대기열 정책 조회 실패. 대기열 없이 진행한다. fairId={}", fairId, e);
            return WaitingRoomPolicy.disabled();
        }
    }

    /** 관리자 조회. 화면에 보여줄 값이므로 캐시를 거치지 않고 DB를 직접 읽는다. */
    @Transactional(readOnly = true)
    public WaitingRoomPolicyResponse get(Long fairId, Long actorUserId) {
        validateFair(fairId);
        operatorAccessService.assertCanManageFair(fairId, actorUserId);
        assertFairExists(fairId);

        WaitingRoomPolicyRow row = policyMapper.selectByFairId(fairId);
        if (row == null) {
            // 아직 설정한 적이 없는 행사. 저장 시 보낼 expectedVersion 0과 기본값을 알려준다.
            return new WaitingRoomPolicyResponse(fairId, false, properties.defaultActiveLimit(), 0, null);
        }
        return new WaitingRoomPolicyResponse(
                fairId, row.isEnabled(), row.getActiveLimit(), row.getVersion(), row.getUpdatedAt());
    }

    /**
     * 대기열을 켜고 끄거나 통과 인원을 바꾼다.
     * EVENT_ADMIN은 배정된 행사만, SUPER_ADMIN은 모든 행사를 바꿀 수 있다.
     */
    @Transactional
    public WaitingRoomPolicyResponse save(
            Long fairId,
            Long actorUserId,
            UpdateWaitingRoomPolicyRequest request
    ) {
        validateFair(fairId);
        validateRequest(request);
        operatorAccessService.assertCanManageFair(fairId, actorUserId);
        assertFairExists(fairId);

        LocalDateTime now = timeProvider.now();
        WaitingRoomPolicyRow current = policyMapper.selectByFairId(fairId);
        if (current == null) {
            // 정책 행이 아직 없을 때 기대하는 값은 0 하나뿐이다. 생략(null)도 거절한다 —
            // 조회 없이 쓴 요청을 통과시키면 낙관적 잠금을 우회하는 구멍이 된다.
            if (request.expectedVersion() == null || request.expectedVersion() != 0) {
                throw new CommonException(ErrorCode.WAITING_ROOM_POLICY_CONFLICT);
            }
            try {
                policyMapper.insertPolicy(
                        fairId, request.enabled(), request.activeLimit(), actorUserId, now);
            } catch (DuplicateKeyException e) {
                // "조회했더니 없어서 INSERT" 사이에 다른 관리자가 먼저 만들었다. version 조건이
                // 지켜주는 UPDATE 경로와 달리 이 구간은 조회와 쓰기가 갈라져 있어 창이 남는데,
                // UK_WAITING_ROOM_FAIR가 그 창을 막아준다. 사용자에게는 동시 수정과 같은
                // 상황이므로 같은 충돌로 돌려보내 다시 조회 후 시도하게 한다.
                throw new CommonException(ErrorCode.WAITING_ROOM_POLICY_CONFLICT, e);
            }
        } else {
            if (request.expectedVersion() == null) {
                throw new CommonException(ErrorCode.WAITING_ROOM_POLICY_CONFLICT);
            }
            int updated = policyMapper.updatePolicy(
                    fairId, request.enabled(), request.activeLimit(),
                    actorUserId, request.expectedVersion(), now);
            if (updated != 1) {
                throw new CommonException(ErrorCode.WAITING_ROOM_POLICY_CONFLICT);
            }
        }

        // 이 인스턴스는 즉시 반영한다. 다른 인스턴스는 TTL만큼 뒤에 따라온다.
        cache.remove(fairId);

        WaitingRoomPolicyRow saved = policyMapper.selectByFairId(fairId);
        return new WaitingRoomPolicyResponse(
                fairId, saved.isEnabled(), saved.getActiveLimit(), saved.getVersion(), saved.getUpdatedAt());
    }

    private void validateFair(Long fairId) {
        if (fairId == null || fairId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void assertFairExists(Long fairId) {
        if (!reservationMapper.existsFair(fairId)) {
            throw new CommonException(ErrorCode.RESERVATION_FAIR_NOT_FOUND);
        }
    }

    private void validateRequest(UpdateWaitingRoomPolicyRequest request) {
        // 상한은 DB CHECK 제약과 같은 값으로 맞춘다. 0 이하면 아무도 통과하지 못해
        // 예약이 완전히 멈추므로 실수로라도 들어가면 안 된다.
        if (request == null
                || request.enabled() == null
                || request.activeLimit() == null
                || request.activeLimit() < 1
                || request.activeLimit() > 100_000) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /** 게이트가 판단에 쓰는 값만 담은 최소 형태. */
    public record WaitingRoomPolicy(boolean enabled, int activeLimit) {
        public static WaitingRoomPolicy disabled() {
            return new WaitingRoomPolicy(false, 0);
        }
    }

    private record CachedPolicy(WaitingRoomPolicy policy, LocalDateTime expiresAt) {
    }
}
