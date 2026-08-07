package com.ms.petopia.api.auth.service;

import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

//saveSignup/peekSignup은 Redis에 실제로 JSON 문자열을 왕복시키는 부분이라, ObjectMapper를 목이 아니라
//진짜로 써서 직렬화/역직렬화가 실제로 어떻게 동작하는지까지 검증한다 (특히 구버전 JSON 호환성)
@ExtendWith(MockitoExtension.class)
class OAuthPendingStoreTest {

    private static final String TEMP_KEY = "temp-key";
    private static final String REDIS_KEY = "oauth_pending:" + TEMP_KEY;
    private static final String PROVIDER = "GOOGLE";
    private static final String OAUTH_ID = "google-oauth-id-1";
    private static final String EMAIL = "test@petopia.com";
    private static final Duration TTL = Duration.ofMinutes(15);

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private OAuthPendingStore oAuthPendingStore;

    @BeforeEach
    void setUp() {
        oAuthPendingStore = new OAuthPendingStore(stringRedisTemplate, new ObjectMapper());
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    void saveSignup_emailVerified가true면_peekSignup으로그대로돌아온다() {
        //Redis에 실제로 들어갈 JSON 문자열을 붙잡아뒀다가, 조회 시 그대로 돌려주게 함
        String[] stored = new String[1];
        willAnswer(invocation -> {
            stored[0] = invocation.getArgument(1);
            return null;
        }).given(valueOperations).set(anyString(), anyString(), eq(TTL));
        willAnswer(invocation -> stored[0]).given(valueOperations).get(REDIS_KEY);

        oAuthPendingStore.saveSignup(TEMP_KEY, EMAIL, PROVIDER, OAUTH_ID, true, TTL);
        OAuthPendingStore.OAuthPendingSignup pending = oAuthPendingStore.peekSignup(TEMP_KEY);

        assertThat(pending.provider()).isEqualTo(PROVIDER);
        assertThat(pending.oauthId()).isEqualTo(OAUTH_ID);
        assertThat(pending.email()).isEqualTo(EMAIL);
        assertThat(pending.emailVerified()).isTrue();
    }

    @Test
    void saveSignup_emailVerified가false면_peekSignup으로그대로돌아온다() {
        String[] stored = new String[1];
        willAnswer(invocation -> {
            stored[0] = invocation.getArgument(1);
            return null;
        }).given(valueOperations).set(anyString(), anyString(), eq(TTL));
        willAnswer(invocation -> stored[0]).given(valueOperations).get(REDIS_KEY);

        oAuthPendingStore.saveSignup(TEMP_KEY, EMAIL, PROVIDER, OAUTH_ID, false, TTL);
        OAuthPendingStore.OAuthPendingSignup pending = oAuthPendingStore.peekSignup(TEMP_KEY);

        assertThat(pending.emailVerified()).isFalse();
    }

    //배포 전환 구간 재현: 이 코드가 배포되기 전에 저장된 pending은 emailVerified 필드 자체가 없는
    //3필드 JSON이다. 이걸 false로 조용히 채우면 completeSignup이 진짜 값(예: 구글의 true)을 잃어버리고
    //틀린 값을 users.email_verified에 저장하게 되므로, 값을 추측하지 말고 거부해야 한다
    @Test
    void peekSignup_emailVerified필드가없는구버전JSON이면_OAUTH_PENDING_NOT_FOUND를던진다() {
        String legacyJson = "{\"provider\":\"" + PROVIDER + "\",\"oauthId\":\"" + OAUTH_ID + "\",\"email\":\"" + EMAIL + "\"}";
        given(valueOperations.get(REDIS_KEY)).willReturn(legacyJson);

        assertThatThrownBy(() -> oAuthPendingStore.peekSignup(TEMP_KEY))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_PENDING_NOT_FOUND);
    }

    @Test
    void peekSignup_저장된값이없으면_null을반환한다() {
        given(valueOperations.get(REDIS_KEY)).willReturn(null);

        assertThat(oAuthPendingStore.peekSignup(TEMP_KEY)).isNull();
    }
}
