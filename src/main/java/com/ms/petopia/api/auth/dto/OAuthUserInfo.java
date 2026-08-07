package com.ms.petopia.api.auth.dto;

/*
 * OAuthProvider가 조회한 소셜 계정 정보를 Service로 넘기는 내부 운반용 객체.
 */
public record OAuthUserInfo(
        String provider,
        String oauthId,
        String email,
        //provider가 이 이메일의 소유를 검증해서 보장해주는지 여부.
        //true인 provider(구글)만 이미 이 이메일로 가입한 유저 있으면 자동 연결
        //검증 클레임이 없는 네이버는 항상 false로 고정해서, 자동 연결 없이 완전 신규로만 취급되게 함
        //만약, 이미 가입된 이메일이라면 가입 신청 시 409를 반환
        boolean emailVerified
) {}
