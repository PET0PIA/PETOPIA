package com.ms.petopia.api.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ms.petopia.api.auth.dto.OAuthUserInfo;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

//빈 이름을 google로 명시 - OAuthService가 Map<String, OAuthProvider>로 주입받아
//URL의 {provider} 값("google")을 그대로 key로 써서 이 구현체를 찾을 수 있게 함
@Component("google")
public class GoogleOAuthProvider implements OAuthProvider {

    private static final String PROVIDER = "GOOGLE";
    private static final String AUTHORIZATION_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

    //RestClient.Builder 빈을 주입받는 대신 직접 생성 - Spring 자동 설정 여부와 상관없이 항상 동작함
    private final RestClient restClient = RestClient.create();

    @Value("${oauth.google.client-id}")
    private String clientId;

    @Value("${oauth.google.client-secret}")
    private String clientSecret;

    @Value("${oauth.google.redirect-uri}")
    private String redirectUri;

    //유저를 구글 로그인/동의 화면으로 보낼 URL 조립.
    //client_id/redirect_uri/response_type/scope는 구글 문서대로 적음.
    @Override
    public String getAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZATION_URI)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "email profile")
                // state는 CSRF 방지 로직: 여기서 만든 값을 Redis에 저장해뒀다가 콜백에서 돌아온 state가 저장된 값과 맞는지 확인해서 위조된 콜백 요청을 걸러낸다
                .queryParam("state", state)
                //scope의 공백처럼 URL에 그대로 못 쓰는 문자를 퍼센트 인코딩(%20 등)해줌.
                //이게 없으면 URI.create()가 "Illegal character"로 거부함
                .encode()
                .build()
                .toUriString();
    }

    //컨트롤러가 콜백을 받으면 이 메서드에 code(구글이 콜백으로 준 일회용 값)를 넘긴다.
    //code 넣으면 email/oauthId 나온다. 구글은 토큰 교환에 state가 필요 없어서 파라미터는 받되 안 씀
    @Override
    public OAuthUserInfo getUserInfo(String code, String state) {
        try {
            //access_token을 입력으로 넘김
            GoogleTokenResponse tokenResponse = exchangeCodeForToken(code); //code -> access_token
            GoogleUserInfoResponse userInfoResponse = fetchUserInfo(tokenResponse.accessToken());   //acess_token -> email/sub

            return new OAuthUserInfo(PROVIDER, userInfoResponse.sub(), userInfoResponse.email());   //변환
        } catch (RestClientException e) {
            //code가 만료/재사용됐거나 구글 쪽 장애 등 서버 문제는 502로 구분
            throw new CommonException(ErrorCode.OAUTH_PROVIDER_ERROR, e);
        }
    }

    //code를 구글 access_token으로 교환.
    //요청 형식과 필드명구글 문서에 정해진 그대로 따름
    private GoogleTokenResponse exchangeCodeForToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        return restClient.post()
                .uri(TOKEN_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(GoogleTokenResponse.class);
    }

    //access_token으로 유저 프로필(email, sub) 조회.
    //형식은 구글 문서 그대로
    private GoogleUserInfoResponse fetchUserInfo(String accessToken) {
        return restClient.get()
                .uri(USERINFO_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(GoogleUserInfoResponse.class);
    }

    //구글 토큰 엔드포인트 응답 형태. 구글 문서에 나온 JSON 필드 중 우리한테 필요한 것만 매핑.
    //구글 응답은 snake_case(access_token)인데 자바 필드는 camelCase라 @JsonProperty로 연결해줌
    private record GoogleTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {}

    //구글 유저정보 엔드포인트 응답 형태 - sub가 구글이 부여한 유저 고유 ID(=oauthId)
    private record GoogleUserInfoResponse(
            String sub,
            String email
    ) {}
}
