package com.ms.petopia.api.auth.dto;

/*
 * Service와 Controller 사이에서만 쓰는 내부 운반용 객체.
 * url은 브라우저를 리다이렉트시킬 곳, state는 컨트롤러가 브라우저 바인딩용
 * 상관관계 쿠키에 담기 위해 필요하다
 * 메서드가 값을 하나 이상 리턴해야 해서 만들게 된 레코드.
 */
public record OAuthAuthorizationStart(
        String url,
        String state
) {}
