package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationDetailResponse;
import com.ms.petopia.api.reservation.dto.ReservationListItemResponse;
import com.ms.petopia.api.reservation.dto.ReservationListResponse;
import com.ms.petopia.api.reservation.dto.ReservationListRow;
import com.ms.petopia.api.reservation.dto.ReservationPetResponse;
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
    private static final String PAYMENT_COMPLETED = "COMPLETED";
    private static final int MAX_PAGE_SIZE = 50;

    private final ReservationMapper reservationMapper;
    private final ReservationTimeProvider timeProvider;
    private final ReservationPetService reservationPetService;

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
        //동반 반려동물은 예약 1건에 딸린 부가 정보라 상세에서만 조회한다(목록에는 넣지 않는다).
        return toDetailResponse(row, timeProvider.now(), reservationPetService.getReservationPets(reservationId));
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

    private ReservationDetailResponse toDetailResponse(
            ReservationListRow row,
            LocalDateTime now,
            List<ReservationPetResponse> pets
    ) {
        String status = row.getReservationStatus();
        String type = row.getReservationType();
        boolean ended = isEnded(row, now);
        boolean qrAvailable = !ended && (CONFIRMED.equals(status) || CHECKED_IN.equals(status));
        // 케밥 노출용 대략 판단. 정확한 마감(12시간 전·취소 마감)은 각 변경/취소 API가 최종 검증한다.
        // 입장 종료된 예약은 화면에서 비활성 처리하므로 두 액션 모두 !ended를 전제로 한다.
        boolean canChangeVisitDate = !ended && ADVANCE.equals(type) && CONFIRMED.equals(status);
        // 유료 확정 예약도 취소 가능하다 — 취소 API가 예약금을 전액 환불하고 CANCELED로 전환한다
        // (ReservationCancellationService 참고). 그래서 금액으로 가리지 않는다.
        boolean canCancel = !ended && (PENDING_PAYMENT.equals(status)
                || (ADVANCE.equals(type) && CONFIRMED.equals(status)));
        return new ReservationDetailResponse(
                row.getReservationId(),
                row.getReservationNo(),
                row.getFairId(),
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
                canCancel,
                row.getPaymentId(),
                paymentMethodLabel(row),
                pets
        );
    }

    /**
     * 상세 화면에 그대로 뿌릴 결제수단 문구를 만든다. 결제 행이 없는 무료 예약이면 null이고,
     * 그때 화면은 결제 줄을 아예 그리지 않는다.
     *
     * payment.method는 결제 완료 전에는 우리가 넣어둔 임시값 "TOSS"라서 그대로 보여주면
     * 사용자에게 의미 없는 문자열이 뜬다. 그래서 완료된 결제만 실제 수단을 노출한다.
     *
     * 환불된 예약은 결제 행이 COMPLETED로 남으므로(환불은 refund 행으로 기록) 수단이 그대로
     * 보인다 - "무엇으로 결제했었는지"는 취소 후에도 남아야 하는 정보다.
     */
    private String paymentMethodLabel(ReservationListRow row) {
        if (row.getPaymentId() == null) return null; // 무료 예약: 결제 행이 아예 없다
        if (!PAYMENT_COMPLETED.equals(row.getPaymentStatus())) return "결제 전";
        String method = row.getPaymentMethod();
        if (method == null || method.isBlank()) return "결제 전";
        String provider = row.getEasyPayProvider();
        return provider == null || provider.isBlank() ? method : method + " (" + provider + ")";
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
