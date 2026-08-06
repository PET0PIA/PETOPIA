package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.dto.OAuthUserInfo;

/*
 * provider(GOOGLE 등)별 OAuth 연동 차이 보완 인터페이스.
 * OAuthService는 이 타입으로만 다뤄서, provider가 늘어나도 분기 없이 구현체만 추가하면 되게 한다.
 */
public interface OAuthProvider {

    //유저를 provider의 로그인 동의 화면으로 보낼 URL 생성 ex)구글의 로그인 화면으로 이동
    String getAuthorizationUrl(String state);

    //콜백에서 받은 code로 (내부적으로 access_token 교환 + 프로필 조회 2단계를 거쳐) 실제 이메일/ID로 바꿔주는 것.
    OAuthUserInfo getUserInfo(String code, String state);
}
