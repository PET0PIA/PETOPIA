package com.ms.petopia.api.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/*
 * 정지된 계정의 access token을 즉시 무효화하기 위한 코드
 * JwtAuthenticationFilter가 인증된 요청마다 이 키의 존재만 확인한다(DB 조회 없이 Redis GET 1회).
 * account_suspended:{userId} 키가 있으면 그 유저의 모든 access token을 즉시 차단한다.
 */
@Component
@RequiredArgsConstructor
public class AccountSuspensionStore {

    private final StringRedisTemplate stringRedisTemplate;

    private String key(Long userId) {
        return "account_suspended:" + userId;
    }

    //계정 정지 - TTL 없이 재활성화(reactivate)로 명시적으로 지울 때까지 유지
    public void suspend(Long userId) {
        stringRedisTemplate.opsForValue().set(key(userId), "1");
    }

    //계정 정지 해제
    public void reactivate(Long userId) {
        stringRedisTemplate.delete(key(userId));
    }

    public boolean isSuspended(Long userId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key(userId)));
    }
}
