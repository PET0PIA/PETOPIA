package com.ms.petopia.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtTokenProvider {

    // Access/Refresh Token을 구분하는 claim 키와 값.
    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    /* application-local.yaml의 jwt.secret 문자열을, 서명/검증에 실제로 쓸 수 있는
        키 객체 형태로 변환해서 들고 있는 것. 이 키를 아는 사람만 유효한 토큰을 만들 수 있고,
        이 키로만 서명이 진짜인지 검증할 수 있다.
    */
    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    // @Value("${jwt.secret}") 처럼 적어두면, 스프링이 .yaml에서 해당 키 값을 읽어와 자동으로 넣어준다.
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ){
        // 문자열 secret을 HMAC(비밀키+내용물) 서명에 쓸 수 있는 SecretKey 객체로 변환
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    // Access Token 발급
    // 로그인 성공 시 이 메서드로 만든 토큰을 클라이언트에게 내려준다.
    public String generateAccessToken(Long userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))   // 토큰 유저
                .claim("role", role)               // 권한체크용 커스텀 정보 추가
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS) // 이 토큰은 Access용이라는 꼬리표
                .issuedAt(now)                      // 발급 시각
                .expiration(new Date(now.getTime() + accessTokenExpiration)) // 만료 시각
                .signWith(secretKey)                // secretKey로 서명 — 위조 방지
                .compact();                         // 위 내용을 문자열 하나로 압축
    }

    // Refresh Token은 Access Token 재발급용이라 role 같은 부가 정보는 필요 없고 사용자 + 언제까지 유효한지만 있으면 된다. 만료 기간 2주.
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH) // 이 토큰은 Refresh용이라는 꼬리표
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    // 토큰을 열어서 안에 적힌 userId를 꺼내온다. Access Token이 아니면 예외가 터진다.
    public Long getUserId(String token) {
        Claims claims = parseAccessTokenClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    // 토큰에 담아둔 role을 꺼내온다. Access Token이 아니면 예외가 터진다.
    public String getRole(String token) {
        return parseAccessTokenClaims(token).get("role", String.class);
    }

    // Access Token 전용 파싱. 서명 검증을 통과했더라도 typ이 access가 아니면 예외를 던진다.
    private Claims parseAccessTokenClaims(String token) {
        Claims claims = parseClaims(token);
        if (!TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new IllegalArgumentException("Access Token이 아닙니다.");
        }
        return claims;
    }

    // verifyWith(secretKey): 이 키로 서명한 게 맞는지 확인
    // parseSignedClaims(token).getPayload(): 검증을 통과했으면 내용물을 반환
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
