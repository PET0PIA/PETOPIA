package com.ms.petopia.api.auth.dto;

/**
 * Service(AuthService.login / TokenService.refresh)와 Controller 사이에서만 쓰는 내부 운반용 객체.
 *
 * 클라이언트에게 나가는 API 응답(예: LoginResponse)이 아니다. Controller가 이 값을 받아서
 * accessToken은 JSON 응답 바디에, refreshToken은 httpOnly 쿠키에 나눠 담는다.
 * refreshToken이 실수로 JSON 응답에 그대로 노출되지 않도록, 응답 DTO와 이 객체를 분리했다.
 *
 * record로 쓴 이유: 생성 후 값이 바뀔 일 없는 단순 운반용 데이터라 getter/생성자를 자동으로
 * 만들어주는 record가 맞고, setter가 열려있는 일반 클래스로 만들 이유가 없다.
 */
public record TokenPair(
        String accessToken,
        String refreshToken
) {}
