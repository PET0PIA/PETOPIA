package com.ms.petopia.api.reservation.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 사용자 노출용 예약번호를 생성한다.
 *
 * <p>{@code R} + 방문일자(8자리) + 무작위 11자리로 총 20자다.
 * 무작위 부분은 혼동하기 쉬운 문자(I, L, O, U)를 뺀 32자 알파벳을 사용해
 * 약 55비트 엔트로피를 확보한다. reservation_no 유니크 제약과 충돌해
 * 예약 생성이 실패하는 일을 막기 위한 것이다.
 */
@Component
public class ReservationNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int RANDOM_LENGTH = 11;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate(LocalDate date) {
        StringBuilder builder = new StringBuilder(20)
                .append('R')
                .append(date.format(DATE_FORMAT));
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            builder.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
