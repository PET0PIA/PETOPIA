package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationListItemResponse;
import com.ms.petopia.api.reservation.dto.ReservationListResponse;
import com.ms.petopia.api.reservation.dto.ReservationListRow;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationQueryService {

    private static final String CONFIRMED = "CONFIRMED";
    private static final String CHECKED_IN = "CHECKED_IN";
    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final int MAX_PAGE_SIZE = 50;

    private final ReservationMapper reservationMapper;
    private final ReservationTimeProvider timeProvider;

    /** 현재 사용자의 예약 목록을 최신 생성 순으로 반환한다. */
    @Transactional(readOnly = true)
    public ReservationListResponse getMyReservations(Long userId, int page, int size) {
        if (userId == null || userId <= 0 || page < 0 || size <= 0 || size > MAX_PAGE_SIZE) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        long totalElements = reservationMapper.countMyReservations(userId);
        int totalPages = (int) ((totalElements + size - 1) / size);
        LocalDateTime now = timeProvider.now();
        List<ReservationListItemResponse> items = reservationMapper.selectMyReservations(
                        userId,
                        (long) page * size,
                        size
                ).stream()
                .map(row -> toResponse(row, now))
                .toList();
        return new ReservationListResponse(
                items,
                page,
                size,
                totalElements,
                totalPages,
                page + 1 < totalPages
        );
    }

    private ReservationListItemResponse toResponse(ReservationListRow row, LocalDateTime now) {
        String status = row.getReservationStatus();
        boolean ended = row.getVisitDate() != null
                && row.getEntryEndTime() != null
                && now.isAfter(LocalDateTime.of(row.getVisitDate(), row.getEntryEndTime()));
        boolean paymentAvailable = PENDING_PAYMENT.equals(status)
                && row.getPaymentExpiresAt() != null
                && now.isBefore(row.getPaymentExpiresAt());
        return new ReservationListItemResponse(
                row.getReservationId(),
                row.getFairName(),
                row.getFairPosterImageUrl(),
                row.getVisitDate(),
                row.getEntryStartTime(),
                row.getEntryEndTime(),
                status,
                ended,
                !ended && (CONFIRMED.equals(status) || CHECKED_IN.equals(status)),
                paymentAvailable,
                row.getAmount(),
                row.getReservedAt(),
                row.getCheckedInAt()
        );
    }
}
