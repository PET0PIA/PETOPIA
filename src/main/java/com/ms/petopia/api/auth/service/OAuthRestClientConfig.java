package com.ms.petopia.api.auth.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/*
 * GoogleOAuthProvider/NaverOAuthProvider가 공유하는 RestClient.
 *
 * RestClient.create()로 직접 만들면 커넥션/읽기 타임아웃이 전혀 안 걸려서
 * 응답을 안 주고 멈추면 그 요청을 처리하던 스레드가 무한정 잡혀있을 수 있다
 * 명시적으로 설정한 RestClient를 여기서 하나만 만들어서 두 provider에 주입한다.
 */

@Configuration
public class OAuthRestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient oauthRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
