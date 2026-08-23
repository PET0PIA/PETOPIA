package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.reservation.dto.AdminReservationItemResponse;
import com.ms.petopia.api.reservation.dto.AdminReservationListResponse;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationQueryService {

    private static final String CANCELED = "CANCELED";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String CHECKED_IN = "CHECKED_IN";
    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String ADVANCE = "ADVANCE";
    private static final String PAYMENT_COMPLETED = "COMPLETED";
    private static final int MAX_PAGE_SIZE = 50;
    private static final int ADMIN_MAX_PAGE_SIZE = 100;

    private final ReservationMapper reservationMapper;
    private final ReservationTimeProvider timeProvider;
    private final ReservationPetService reservationPetService;
    private final FairAdminAccessGuard fairAdminAccessGuard;

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

    /**
     * 담당 행사(EVENT_ADMIN) 또는 전체(SUPER_ADMIN)의 예약자별 상세 목록을 반환한다.
     * 운영일·상태 필터는 선택값이라 null이면 걸지 않는다.
     *
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 그 행사 담당 관리자가 아닐 때
     */
    @Transactional(readOnly = true)
    public AdminReservationListResponse getFairReservationsForAdmin(
            Long fairId, LocalDate visitDate, String status, int page, int size
    ) {
        if (fairId == null || fairId <= 0 || page < 0 || size <= 0 || size > ADMIN_MAX_PAGE_SIZE) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        fairAdminAccessGuard.checkAssigned(fairId);

        long totalElements = reservationMapper.countByFairForAdmin(fairId, visitDate, status);
        int totalPages = size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
        List<AdminReservationItemResponse> items = reservationMapper
                .selectByFairForAdmin(fairId, visitDate, status, (long) page * size, size)
                .stream()
                .map(AdminReservationItemResponse::from)
                .toList();
        return new AdminReservationListResponse(items, page, size, totalElements, totalPages);
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
                paymentDeadline(row),
                row.getAmount(),
                row.getReservedAt(),
                row.getCheckedInAt(),
                isCanceledByFairCancellation(row)
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
        // 마감 시각을 화면에 알려주고, 케밥 노출 판단에도 반영한다. 예전에는 상태·유형만 보고
        // 버튼을 열어줘서, 마감이 지난 예약도 눌러본 뒤에야 R018·R019로 거절됐다.
        // 입장 종료된 예약은 화면에서 비활성 처리하므로 두 액션 모두 !ended를 전제로 한다.
        LocalDateTime changeDeadlineAt = deadlineAt(
                row, row.getChangeDeadlineHours(), ReservationDeadlinePolicy.DEFAULT_CHANGE_DEADLINE_HOURS
        );
        LocalDateTime cancelDeadlineAt = deadlineAt(
                row, row.getCancelDeadlineHours(), ReservationDeadlinePolicy.DEFAULT_CANCEL_DEADLINE_HOURS
        );
        boolean canChangeVisitDate = !ended && ADVANCE.equals(type) && CONFIRMED.equals(status)
                && !isPast(changeDeadlineAt, now);
        // 유료 확정 예약도 취소 가능하다 — 취소 API가 예약금을 전액 환불하고 CANCELED로 전환한다
        // (ReservationCancellationService 참고). 그래서 금액으로 가리지 않는다.
        // 결제 대기 예약에는 마감을 적용하지 않는다 - 취소 API도 그 상태는 마감 검사 없이 받아준다.
        boolean canCancel = !ended && (PENDING_PAYMENT.equals(status)
                || (ADVANCE.equals(type) && CONFIRMED.equals(status) && !isPast(cancelDeadlineAt, now)));
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
                paymentDeadline(row),
                row.getAmount(),
                row.getReservedAt(),
                row.getCheckedInAt(),
                canChangeVisitDate,
                canCancel,
                changeDeadlineAt,
                cancelDeadlineAt,
                row.getPaymentId(),
                paymentMethodLabel(row),
                pets,
                isCanceledByFairCancellation(row)
        );
    }

    /**
     * 취소·변경 마감 시각을 계산한다. 행사가 정한 기한(입장 몇 시간 전)이 없으면 기본값을 쓴다.
     *
     * <p>음수 기한은 잘못 저장된 설정이다(행사 신청·수정에서 막지만 과거 데이터가 있을 수 있다).
     * 그때는 null을 내려 "마감 없음"으로 두고, 실제 취소·변경 API가 400으로 거절하게 한다 -
     * 여기서 억지로 시각을 만들면 화면에 없는 기한이 그려진다.
     */
    private LocalDateTime deadlineAt(ReservationListRow row, Integer configuredHours, int defaultHours) {
        if (row.getVisitDate() == null || row.getEntryStartTime() == null) {
            return null;
        }
        int hours = configuredHours == null ? defaultHours : configuredHours;
        if (hours < 0) {
            return null;
        }
        return LocalDateTime.of(row.getVisitDate(), row.getEntryStartTime()).minusHours(hours);
    }

    /** 마감 시각이 없으면(계산 불가) 지나지 않은 것으로 본다 - 판단은 각 API가 최종적으로 한다. */
    private boolean isPast(LocalDateTime deadlineAt, LocalDateTime now) {
        return deadlineAt != null && now.isAfter(deadlineAt);
    }

    /**
     * 주최측 행사 취소로 자동 취소된 예약인지 판단한다.
     *
     * <p>취소한 주체를 canceled_by로 가린다. 예약을 CANCELED로 바꾸는 경로는 둘뿐이라서다 -
     * 사용자 자진취소({@link ReservationCancellationService})는 항상 본인 user_id를 남기고,
     * 행사 취소 정리({@link ReservationFairCancelSyncService})는 사람이 아니므로 NULL을 남긴다.
     * 취소 사유 문구를 비교하지 않는 이유는, 문구가 바뀌면 조용히 오작동하기 때문이다.
     */
    private boolean isCanceledByFairCancellation(ReservationListRow row) {
        return CANCELED.equals(row.getReservationStatus()) && row.getCanceledBy() == null;
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

    /**
     * 화면에 노출할 결제 제한시각. 결제 대기 상태일 때만 준다.
     *
     * <p>결제가 끝나거나 취소된 예약의 payment_expires_at은 원장에 그대로 남아 있어서,
     * 거르지 않고 내보내면 "이미 확정된 예약에 지난 마감시각이 붙어 있는" 응답이 된다.
     */
    private LocalDateTime paymentDeadline(ReservationListRow row) {
        return PENDING_PAYMENT.equals(row.getReservationStatus()) ? row.getPaymentExpiresAt() : null;
    }

    private boolean isPaymentAvailable(ReservationListRow row, LocalDateTime now) {
        return PENDING_PAYMENT.equals(row.getReservationStatus())
                && row.getPaymentExpiresAt() != null
                && now.isBefore(row.getPaymentExpiresAt());
    }
}
