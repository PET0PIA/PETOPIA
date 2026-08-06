package com.ms.petopia.api.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/*
    신원 정보 전달용
    실제 정보를 저장
    TTL = 로그인 2분/ 가입 15분
    콜백을 받고 oauth가입/혹은 원래 있던 아이디를 소셜과 연결 할 때 OauthStateStore에서 검증 통과한 다음 데이터를 넘겨주는 역할
    users 테이블에 넣으려면 nickname, phone 등 다른 정보들도 받아야 하기 때문에 pending으로 넘김
 */

@Component
@RequiredArgsConstructor
public class OAuthPendingStore {

    private final StringRedisTemplate stringRedisTemplate;

    public void saveLogin(String code, Long userId, Duration ttl) {
        //Redis는 나눠져 있지 않고 전체가 하나의 Map 이기 때문에 key 앞에 문자열을 붙여줘야 key가 겹치지 않을 수 있다.
        String key = "oauth_login:" + code;
        stringRedisTemplate.opsForValue().set(key, userId.toString(), ttl); //Long -> String (userId)
    }

    //조회+삭제(GETDEL)를 원자적으로 처리해 동시에 같은 토큰으로 refresh 요청이 와도 하나만 성공하게 함
    public Long consumeLogin(String code) {
        String key = "oauth_login:" + code;
        String value = stringRedisTemplate.opsForValue().getAndDelete(key);
        return value == null ? null : Long.valueOf(value);
        //value = userId
    }


    //신규 유저 가입 시 redis 저장
    public void saveSignup(String tempKey, String email, String provider, String oauthId, Duration ttl) {
        String key = "oauth_pending:" + tempKey;
        String value = provider + ":" + oauthId + ":" + email;   //redis는 key:value(값 하나)만 되기 때문에 문자열로 나열함
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    //값 다시 쪼개 리턴
    public OAuthPendingSignup consumeSignup(String tempKey) {
        String key = "oauth_pending:" + tempKey;
        String value = stringRedisTemplate.opsForValue().getAndDelete(key);
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":", 3);
        return new OAuthPendingSignup(parts[0], parts[1], parts[2]);
    }

    public record OAuthPendingSignup(String provider, String oauthId, String email) {

    }

}
