package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationAvailabilityDateResponse;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityDateRow;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityFair;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityResponse;
import com.ms.petopia.api.reservation.dto.ReservationMyActiveDateRow;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationAvailabilityService {

    private final ReservationMapper reservationMapper;
    private final ReservationTimeProvider timeProvider;

    /**
     * 예약 화면에 표시할 행사별 예약금과 날짜별 잔여 사전예약 정원을 반환한다.
     *
     * <p>비로그인도 부르는 공개 조회라 userId는 null일 수 있다. 로그인 상태라면 그 사람이
     * 이 행사에서 이미 잡아둔 날짜를 함께 실어 준다 - 화면이 그 날짜를 미리 "이미 예약함"으로
     * 표시해, 결제 버튼을 누른 뒤에야 R005로 막히는 흐름을 없애기 위해서다.
     *
     * @param userId 로그인 사용자 ID. 비로그인이면 null
     */
    @Transactional(readOnly = true)
    public ReservationAvailabilityResponse getAvailability(Long fairId, Long userId) {
        if (fairId == null || fairId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        ReservationAvailabilityFair fair = reservationMapper.selectAvailabilityFair(fairId);
        if (fair == null) {
            throw new CommonException(ErrorCode.RESERVATION_FAIR_NOT_FOUND);
        }

        LocalDateTime now = timeProvider.now();
        LocalDate today = now.toLocalDate();
        validateReservableFair(fair, today);

        // 내 예약은 로그인했을 때만 조회한다. 비로그인 화면은 날짜 카드에 붙일 표시가 없다.
        Map<LocalDate, ReservationMyActiveDateRow> mine = userId == null || userId <= 0
                ? Map.of()
                : reservationMapper.selectMyActiveReservationDates(fairId, userId).stream()
                        // 같은 날짜에 활성 예약은 최대 1건이지만(중복 예약 차단), 방어적으로 먼저 온 건을 남긴다.
                        .collect(Collectors.toMap(
                                ReservationMyActiveDateRow::getVisitDate,
                                Function.identity(),
                                (first, second) -> first));

        List<ReservationAvailabilityDateResponse> dates = reservationMapper
                .selectAvailabilityDates(fairId, today, now)
                .stream()
                .map(row -> toResponse(row, mine.get(row.getVisitDate())))
                .toList();

        return new ReservationAvailabilityResponse(fairId, fair.getReservationFee(), fair.isPetAllowed(), dates);
    }

    private void validateReservableFair(ReservationAvailabilityFair fair, LocalDate today) {
        if (fair.getPublishedAt() == null
                || fair.getCanceledAt() != null
                || fair.getReservationStartDate() == null
                || fair.getReservationEndDate() == null
                || today.isBefore(fair.getReservationStartDate())
                || today.isAfter(fair.getReservationEndDate())) {
            throw new CommonException(ErrorCode.RESERVATION_NOT_OPEN);
        }
        if (fair.getReservationFee() < 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private ReservationAvailabilityDateResponse toResponse(
            ReservationAvailabilityDateRow row,
            ReservationMyActiveDateRow myReservation
    ) {
        long remainingCapacity = Math.max((long) row.getCapacity() - row.getOccupiedCount(), 0);
        // available은 잔여석만 본다. 내가 이미 예약한 날은 "마감"이 아니라 "이미 예약함"이고,
        // 화면이 두 경우에 서로 다른 문구·안내를 보여줘야 하므로 플래그를 섞지 않는다.
        return new ReservationAvailabilityDateResponse(
                row.getVisitDate(),
                row.getEntryStartTime(),
                row.getEntryEndTime(),
                remainingCapacity,
                remainingCapacity > 0,
                myReservation == null ? null : myReservation.getReservationId(),
                myReservation == null ? null : myReservation.getStatus()
        );
    }
}
