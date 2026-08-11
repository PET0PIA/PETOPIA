package com.ms.petopia.api.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class LoginAttemptStore {

    private final StringRedisTemplate stringRedisTemplate;

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private String key(String email) {
        return "login_attempt:" + email;
    }

    //지금 이 이메일이 잠긴 상태인지 확인
    public boolean isBlocked(String email) {
        String value = stringRedisTemplate.opsForValue().get(key(email));
        if (value == null) {
            return false;
        }
        return Integer.parseInt(value) >= MAX_ATTEMPTS;
    }

    //로그인 실패 시 호출 — 카운트 +1, 처음 생기는 키면 TTL도 설정
    public void recordFailure(String email) {
        Long count = stringRedisTemplate.opsForValue().increment(key(email));
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key(email), WINDOW);
        }
    }

    //로그인 성공 시 호출 — 카운트 리셋
    public void reset(String email) {
        stringRedisTemplate.delete(key(email));
    }

}
