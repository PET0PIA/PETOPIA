package com.ms.petopia.api.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private final StringRedisTemplate stringRedisTemplate;

    /*
    redis : 자바의 Map<String, String>을 애플리케이션 밖에 떼어놓은 것
    - SET: key에 value를 저장 (stringRedisTemplate.opsForValue().set(key, value))
    - GET: key로 value를 꺼내옴 (stringRedisTemplate.opsForValue().get(key))
    - DEL: key를 지움 (stringRedisTemplate.delete(key))
     */

    public void save(String tokenHash, Long userId, Duration ttl) {
        //Redis는 나눠져 있지 않고 전체가 하나의 Map 이기 때문에 key 앞에 문자열을 붙여줘야 key가 겹치지 않을 수 있다.
        String key = "refresh_token:" + tokenHash;
        stringRedisTemplate.opsForValue().set(key, userId.toString(), ttl);
    }

    public Long findUserId(String tokenHash) {
        String key = "refresh_token:" + tokenHash;
        String value = stringRedisTemplate.opsForValue().get(key);
        return value == null ? null : Long.valueOf(value);
    }

    public void revoke(String tokenHash) {
        String key = "refresh_token:" + tokenHash;
        stringRedisTemplate.delete(key);
    }


}
