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

    //OAuthRestClientConfig가 타임아웃까지 설정해서 만들어준 공용 빈을 주입받음
    private final RestClient restClient;

    @Value("${oauth.naver.client-id}")
    private String clientId;

    @Value("${oauth.naver.client-secret}")
    private String clientSecret;

    @Value("${oauth.naver.redirect-uri}")
    private String redirectUri;

    public NaverOAuthProvider(RestClient oauthRestClient) {
        this.restClient = oauthRestClient;
    }

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
            validate(userInfoResponse);

            //네이버는 구글의 email_verified 같은 검증 클레임이 없음 - 항상 false로 고정해서
            //OAuthService가 이 이메일로 자동 연결(다른 사람 계정에 잘못 연결)을 시도하지 않게 함
            return new OAuthUserInfo(PROVIDER, userInfoResponse.response().id(), userInfoResponse.response().email(), false);
        } catch (RestClientException e) {
            throw new CommonException(ErrorCode.OAUTH_PROVIDER_ERROR, e);
        }
    }

    //네이버는 인증/파라미터 오류도 HTTP 200 + resultcode(예: "024","025")로 내려주는 경우가 있어서
    //RestClient의 4xx/5xx 예외 처리만으론 못 걸러냄 - 응답 내용 자체를 직접 검증해야 함
    private void validate(NaverUserInfoResponse response) {
        boolean valid = response != null
                && "00".equals(response.resultcode())
                && response.response() != null
                && response.response().id() != null && !response.response().id().isBlank()
                && response.response().email() != null && !response.response().email().isBlank();

        if (!valid) {
            throw new CommonException(ErrorCode.OAUTH_PROVIDER_ERROR);
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

    //네이버 유저정보 엔드포인트 응답 - 구글과 달리 실제 데이터가 response 객체 안에 한 번 더 감싸져 있고,
    //성공/실패 여부가 HTTP 상태코드가 아니라 resultcode 필드로 옴("00"이 성공)
    //{"resultcode":"00","message":"success","response":{"id":"...","email":"..."}}
    private record NaverUserInfoResponse(String resultcode, String message, NaverProfile response) {}

    //id가 네이버가 부여한 유저 고유 ID(=oauthId)
    private record NaverProfile(String id, String email) {}
}
