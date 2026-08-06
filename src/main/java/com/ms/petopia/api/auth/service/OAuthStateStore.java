package com.ms.petopia.api.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/*
    신원 위조 방지용 코드
    저장하는 값은 없음
    state는 단순 랜덤 문자열(전에 만들어 둔 메소드를 사용해)
    TTL = 5분
    oauth 콜백을 받을 때 콜백이 위조가 아니고 진짜인지 확인용
 */

@Component
@RequiredArgsConstructor
public class OAuthStateStore {

    private final StringRedisTemplate stringRedisTemplate;

    public void save(String state, Duration ttl) {
        //Redis는 나눠져 있지 않고 전체가 하나의 Map 이기 때문에 key 앞에 문자열을 붙여줘야 key가 겹치지 않을 수 있다.
        String key = "oauth_state:" + state;
        stringRedisTemplate.opsForValue().set(key, "1", ttl);

    }

    public boolean validate(String state) {
        String key = "oauth_state:" + state;
        String value = stringRedisTemplate.opsForValue().getAndDelete(key);  // 있으면 꺼내면서 바로 삭제(재사용 방지)
        return value != null;   // null이 아니면 = 우리가 진짜 발급했던 state였다는 뜻
    }

}
