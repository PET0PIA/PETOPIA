package com.ms.petopia.api.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ms.petopia.api.auth.dto.OAuthUserInfo;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

//빈 이름을 naver로 명시 - OAuthService가 Map<String, OAuthProvider>로 주입받아
//URL의 {provider} 값("naver")을 그대로 key로 써서 이 구현체를 찾을 수 있게 함
@Component("naver")
public class NaverOAuthProvider implements OAuthProvider {

    private static final String PROVIDER = "NAVER";
    private static final String AUTHORIZATION_URI = "https://nid.naver.com/oauth2.0/authorize";
    private static final String TOKEN_URI = "https://nid.naver.com/oauth2.0/token";
    private static final String USERINFO_URI = "https://openapi.naver.com/v1/nid/me";

    private final RestClient restClient = RestClient.create();

    @Value("${oauth.naver.client-id}")
    private String clientId;

    @Value("${oauth.naver.client-secret}")
    private String clientSecret;

    @Value("${oauth.naver.redirect-uri}")
    private String redirectUri;

    //유저를 네이버 로그인/동의 화면으로 보낼 URL 조립.
    //구글과 달리 scope 파라미터가 없음 - 네이버는 권한 범위를 네이버 개발자 콘솔에서 앱 등록 시 미리 설정해두는 방식이라
    @Override
    public String getAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZATION_URI)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();
    }

    //네이버는 토큰 교환 요청에도 state를 다시 요구함 (콜백 때 받은 state를 그대로 넘김)
    @Override
    public OAuthUserInfo getUserInfo(String code, String state) {
        try {
            NaverTokenResponse tokenResponse = exchangeCodeForToken(code, state);
            NaverUserInfoResponse userInfoResponse = fetchUserInfo(tokenResponse.accessToken());

            return new OAuthUserInfo(PROVIDER, userInfoResponse.response().id(), userInfoResponse.response().email());
        } catch (RestClientException e) {
            throw new CommonException(ErrorCode.OAUTH_PROVIDER_ERROR, e);
        }
    }

    //code를 네이버 access_token으로 교환.
    //네이버 문서 예시가 GET 방식(쿼리파라미터)이라 그대로 따름 - 구글의 POST form-urlencoded와 다른 부분
    private NaverTokenResponse exchangeCodeForToken(String code, String state) {
        String tokenUrl = UriComponentsBuilder.fromUriString(TOKEN_URI)
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("code", code)
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();

        return restClient.get()
                .uri(tokenUrl)
                .retrieve()
                .body(NaverTokenResponse.class);
    }

    //access_token으로 유저 프로필(email, id) 조회
    private NaverUserInfoResponse fetchUserInfo(String accessToken) {
        return restClient.get()
                .uri(USERINFO_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(NaverUserInfoResponse.class);
    }

    //네이버 토큰 엔드포인트 응답 형태 - 구글과 동일하게 access_token(snake_case) 필드라 @JsonProperty로 매핑
    private record NaverTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {}

    //네이버 유저정보 엔드포인트 응답 - 구글과 달리 실제 데이터가 response 객체 안에 한 번 더 감싸져 있음
    //{"resultcode":"00","message":"success","response":{"id":"...","email":"..."}}
    private record NaverUserInfoResponse(NaverProfile response) {}

    //id가 네이버가 부여한 유저 고유 ID(=oauthId)
    private record NaverProfile(String id, String email) {}
}
