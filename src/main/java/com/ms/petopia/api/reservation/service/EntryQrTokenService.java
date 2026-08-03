package com.ms.petopia.api.reservation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class EntryQrTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    public EntryQrTokenService(@Value("${petopia.reservation.entry-qr-secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("입장 QR 비밀키는 32자 이상이어야 합니다.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 동일 예약은 언제 조회해도 같은 서명 토큰을 얻는다. */
    public String tokenForReservation(Long reservationId) {
        String payload = "v1.r." + reservationId;
        return payload + "." + sign(payload);
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("입장 QR 서명을 생성할 수 없습니다.", exception);
        }
    }
}
