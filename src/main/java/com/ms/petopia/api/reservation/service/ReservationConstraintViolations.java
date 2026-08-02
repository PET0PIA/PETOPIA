package com.ms.petopia.api.reservation.service;

import org.springframework.dao.DuplicateKeyException;

import java.util.Locale;

/**
 * reservations 테이블의 유니크 제약 위반을 구분한다.
 *
 * <p>예약번호 제약(UK_RESERVATION_NO)과 활성 예약 제약(UK_RESERVATION_ACTIVE_USER_FAIR)이
 * 모두 DuplicateKeyException으로 올라오므로 제약 이름으로 판별해야 한다.
 */
final class ReservationConstraintViolations {

    /** 같은 사용자·행사에 활성 예약이 하나만 존재하도록 강제하는 제약. */
    private static final String ACTIVE_USER_FAIR = "UK_RESERVATION_ACTIVE_USER_FAIR";

    private ReservationConstraintViolations() {
    }

    /**
     * 활성 예약 중복 제약 위반인지 판단한다.
     *
     * <p>번역된 예외 메시지에 제약 이름이 남지 않는 경우가 있어 최하위 원인 메시지까지 확인한다.
     * DB·드라이버 설정에 따라 제약 이름 대소문자가 달라질 수 있어 대소문자는 무시한다.
     */
    static boolean isActiveReservationDuplicate(DuplicateKeyException exception) {
        return containsConstraintName(exception.getMessage())
                || containsConstraintName(exception.getMostSpecificCause().getMessage());
    }

    private static boolean containsConstraintName(String message) {
        return message != null && message.toUpperCase(Locale.ROOT).contains(ACTIVE_USER_FAIR);
    }
}
