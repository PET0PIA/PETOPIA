package com.ms.petopia.api.auth.dto;

/*
 * OAuthProvider가 조회한 소셜 계정 정보를 Service로 넘기는 내부 운반용 객체.
 */
public record OAuthUserInfo(
        String provider,
        String oauthId,
        String email
) {}
