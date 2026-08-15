package com.ms.petopia.api.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 상담사 "입력 중" 신호.
 *
 * <p><b>DB에 쓰지 않는다.</b> 상담 한 건에 수십~수백 번 발생하고 3초 뒤면 의미가 없어지는
 * 값이라, 영속화할 가치가 없고 하면 안 된다.
 *
 * <p>TTL(6초)이 하트비트 주기(3초)의 두 배인 것이 핵심이다.
 * <ul>
 *   <li>하트비트가 한 번 유실돼도 표시가 깜빡이지 않는다.</li>
 *   <li>상담사가 브라우저를 그냥 닫아 중단 신호를 못 보내도 6초 뒤 저절로 사라진다 -
 *       "입력 중"이 영원히 켜진 채 남는 상태가 구조적으로 불가능하다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ChatTypingStore {

    private static final String KEY_PREFIX = "chat:typing:";

    /** 하트비트 주기(3초)의 2배. 위 클래스 주석의 이유로 이 관계를 깨지 않는다. */
    private static final Duration TTL = Duration.ofSeconds(6);

    private final StringRedisTemplate stringRedisTemplate;

    /** 하트비트. 같은 키를 계속 덮어써 TTL을 갱신한다. */
    public void markTyping(Long conversationId, Long adminId) {
        stringRedisTemplate.opsForValue()
                .set(KEY_PREFIX + conversationId, String.valueOf(adminId), TTL);
    }

    public void clearTyping(Long conversationId) {
        stringRedisTemplate.delete(KEY_PREFIX + conversationId);
    }

    /** 새로 연결한 클라이언트에 현재 상태를 알려줄 때 쓴다(이벤트는 재전송하지 않으므로). */
    public boolean isTyping(Long conversationId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + conversationId));
    }
}
