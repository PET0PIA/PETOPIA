package com.ms.petopia.api.auth.service;

import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;

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


    //신규 유저 가입 시 redis 저장.
    //콜론으로 이어붙인 문자열 대신 JSON으로 직렬화함
    //emailVerified도 같이 보존해야 함 - completeSignup이 이 값을 그대로 users.email_verified에 저장하기 때문에
    public void saveSignup(String tempKey, String email, String provider, String oauthId, boolean emailVerified, Duration ttl) {
        if (email == null || email.isBlank() || oauthId == null || oauthId.isBlank()) {
            throw new CommonException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }

        String key = "oauth_pending:" + tempKey;
        String value = writeJson(new OAuthPendingSignup(provider, oauthId, email, emailVerified));
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    //비파괴적 조회
    public OAuthPendingSignup peekSignup(String tempKey) {
        String key = "oauth_pending:" + tempKey;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        OAuthPendingSignup pending = readJson(value);
        if (pending.emailVerified() == null) {
            throw new CommonException(ErrorCode.OAUTH_PENDING_NOT_FOUND);
        }
        return pending;
    }

    //DB 커밋이 성공적으로 끝난 뒤에만 호출해서 실제로 지움 (OAuthService에서 트랜잭션 커밋 이후로 미뤄서 호출)
    public void deleteSignup(String tempKey) {
        stringRedisTemplate.delete("oauth_pending:" + tempKey);
    }

    private String writeJson(OAuthPendingSignup pending) {
        try {
            return objectMapper.writeValueAsString(pending);
        } catch (JacksonException e) {
            throw new CommonException(ErrorCode.OAUTH_PROVIDER_ERROR, e);
        }
    }

    private OAuthPendingSignup readJson(String value) {
        try {
            return objectMapper.readValue(value, OAuthPendingSignup.class);
        } catch (JacksonException e) {
            throw new CommonException(ErrorCode.OAUTH_PROVIDER_ERROR, e);
        }
    }

    public record OAuthPendingSignup(String provider, String oauthId, String email, Boolean emailVerified) {

    }

}
