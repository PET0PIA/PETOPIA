package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationDetailResponse;
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
    private static final String ADVANCE = "ADVANCE";
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

    /** 본인 예약 1건의 상세를 반환한다. 없거나 본인 소유가 아니면 R010. */
    @Transactional(readOnly = true)
    public ReservationDetailResponse getReservationDetail(Long reservationId, Long userId) {
        if (reservationId == null || reservationId <= 0 || userId == null || userId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ReservationListRow row = reservationMapper.selectReservationForOwner(reservationId, userId);
        if (row == null) {
            throw new CommonException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        return toDetailResponse(row, timeProvider.now());
    }

    private ReservationListItemResponse toResponse(ReservationListRow row, LocalDateTime now) {
        String status = row.getReservationStatus();
        boolean ended = isEnded(row, now);
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
                isPaymentAvailable(row, now),
                row.getAmount(),
                row.getReservedAt(),
                row.getCheckedInAt()
        );
    }

    private ReservationDetailResponse toDetailResponse(ReservationListRow row, LocalDateTime now) {
        String status = row.getReservationStatus();
        String type = row.getReservationType();
        boolean ended = isEnded(row, now);
        boolean qrAvailable = !ended && (CONFIRMED.equals(status) || CHECKED_IN.equals(status));
        // 케밥 노출용 대략 판단. 정확한 마감(12시간 전·취소 마감)은 각 변경/취소 API가 최종 검증한다.
        boolean canChangeVisitDate = ADVANCE.equals(type) && CONFIRMED.equals(status) && !ended;
        boolean canCancel = PENDING_PAYMENT.equals(status)
                || (ADVANCE.equals(type) && CONFIRMED.equals(status) && row.getAmount() == 0 && !ended);
        return new ReservationDetailResponse(
                row.getReservationId(),
                row.getFairName(),
                row.getFairPosterImageUrl(),
                row.getVisitDate(),
                row.getEntryStartTime(),
                row.getEntryEndTime(),
                status,
                type,
                ended,
                qrAvailable,
                isPaymentAvailable(row, now),
                row.getAmount(),
                row.getReservedAt(),
                row.getCheckedInAt(),
                canChangeVisitDate,
                canCancel
        );
    }

    private boolean isEnded(ReservationListRow row, LocalDateTime now) {
        return row.getVisitDate() != null
                && row.getEntryEndTime() != null
                && now.isAfter(LocalDateTime.of(row.getVisitDate(), row.getEntryEndTime()));
    }

    private boolean isPaymentAvailable(ReservationListRow row, LocalDateTime now) {
        return PENDING_PAYMENT.equals(row.getReservationStatus())
                && row.getPaymentExpiresAt() != null
                && now.isBefore(row.getPaymentExpiresAt());
    }
}
