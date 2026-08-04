package com.ms.petopia.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// DB엔 SHA-256 해시만 저장해야 하는 토큰들을 위한 공용 유틸.
public final class TokenHashUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // 대문자+숫자 중 헷갈리는 문자(0/O, 1/I/L) 제외. 사람이 직접 입력하는 인증 코드용.
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 6;

    private TokenHashUtil() {
    }

    // 원본 토큰 생성. URL/이메일 본문에 그대로 넣어도 되는 URL-safe 문자열.
    public static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // 6자리 인증 코드 생성
    // generateRawToken() 너무 길어 6자리로 수정.
    public static String generateVerificationCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(SECURE_RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    // 원본 토큰 -> SHA-256 해시(16진수 64자). DB엔 이 값만 저장한다.
    public static String sha256(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 지원하지 않는 환경입니다.", e);
        }
    }
}
