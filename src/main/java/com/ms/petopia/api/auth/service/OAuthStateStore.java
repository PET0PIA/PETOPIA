package com.ms.petopia.api.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/*
    신원 위조 방지용 코드
    state는 단순 랜덤 문자열(전에 만들어 둔 메소드를 사용해)
    TTL = 5분
    oauth 콜백을 받을 때 콜백이 위조가 아니고 진짜인지 확인용.
    value로 provider를 저장해서, 구글용으로 발급된 state를 네이버 콜백에 붙이는 것도 막는다
 */

@Component
@RequiredArgsConstructor
public class OAuthStateStore {

    private final StringRedisTemplate stringRedisTemplate;

    public void save(String provider, String state, Duration ttl) {
        //Redis는 나눠져 있지 않고 전체가 하나의 Map 이기 때문에 key 앞에 문자열을 붙여줘야 key가 겹치지 않을 수 있다.
        String key = "oauth_state:" + state;
        stringRedisTemplate.opsForValue().set(key, provider.toLowerCase(), ttl);
    }

    public boolean validate(String provider, String state) {
        String key = "oauth_state:" + state;
        String storedProvider = stringRedisTemplate.opsForValue().getAndDelete(key);  // 있으면 꺼내면서 바로 삭제(재사용 방지)
        //발급 당시 provider랑 콜백의 provider가 같아야만 통과
        return storedProvider != null && storedProvider.equals(provider.toLowerCase());
    }

}
