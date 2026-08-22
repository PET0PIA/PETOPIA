package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.CreateOnsiteReservationRequest;
import com.ms.petopia.api.reservation.dto.CreateOnsiteReservationResponse;
import com.ms.petopia.api.reservation.dto.OnsiteReservationCreationContext;
import com.ms.petopia.api.reservation.dto.ReservationInsertRow;
import com.ms.petopia.api.reservation.dto.ReservationUserSnapshot;
import com.ms.petopia.api.reservation.mapper.OnsiteReservationMapper;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.api.reservation.model.OnsiteSalesStatus;
import com.ms.petopia.api.reservation.model.ReservationType;
import com.ms.petopia.api.statistics.event.ReservationStatusChangedEvent;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class OnsiteReservationService {

    public static final String ONSITE_TERMS_VERSION = "onsite-no-refund-v1";
    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String CONFIRMED = "CONFIRMED";
    private static final long PAYMENT_WAIT_MINUTES = ReservationPaymentPolicy.PAYMENT_WAIT_MINUTES;

    private final OnsiteReservationMapper onsiteReservationMapper;
    private final ReservationMapper reservationMapper;
    private final ReservationNumberGenerator reservationNumberGenerator;
    private final ReservationTimeProvider timeProvider;
    private final EntryQrService entryQrService;
    private final ReservationPetService reservationPetService;

    // 실시간 예약 현황용 이벤트 발행
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 로그인 회원 본인의 당일 현장 직접예매를 만든다.
     * 현장예매는 사전예약 정원을 조회하거나 차감하지 않는다.
     */
    @Transactional
    public CreateOnsiteReservationResponse create(
            Long fairId,
            Long userId,
            CreateOnsiteReservationRequest request
    ) {
        validateIdentifiers(fairId, userId);

        LocalDateTime now = timeProvider.now();
        LocalDate today = now.toLocalDate();
        OnsiteReservationCreationContext context =
                onsiteReservationMapper.selectCreationContextForUpdate(fairId, today);
        if (context == null) {
            if (!reservationMapper.existsFair(fairId)) {
                throw new CommonException(ErrorCode.RESERVATION_FAIR_NOT_FOUND);
            }
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }

        validateFairAndSales(context, now);

        ReservationUserSnapshot user = reservationMapper.selectUserSnapshot(userId);
        validateUser(user);

        if (reservationMapper.existsActiveReservation(fairId, userId, today)) {
            throw new CommonException(ErrorCode.DUPLICATED_RESERVATION);
        }

        long amount = context.getOnsitePrice();
        boolean paymentRequired = amount > 0;
        validateTerms(paymentRequired, request);

        String status = paymentRequired ? PENDING_PAYMENT : CONFIRMED;
        LocalDateTime paymentExpiresAt = paymentRequired ? now.plusMinutes(PAYMENT_WAIT_MINUTES) : null;
        String reservationNo = reservationNumberGenerator.generate(today);

        ReservationInsertRow row = ReservationInsertRow.builder()
                .reservationNo(reservationNo)
                .fairId(fairId)
                .userId(userId)
                .visitDate(today)
                .reservationType(ReservationType.ONSITE_DIRECT.name())
                .status(status)
                .reservationAmount(amount)
                .reserverName(user.getNickname())
                .reserverPhone(user.getPhone())
                .reserverEmail(user.getEmail())
                .channel("ONLINE")
                .agreedTerms(paymentRequired)
                .reservationTermsVersion(paymentRequired ? ONSITE_TERMS_VERSION : null)
                .reservationTermsAgreedAt(paymentRequired ? now : null)
                .reservedAt(paymentRequired ? null : now)
                .paymentExpiresAt(paymentExpiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            reservationMapper.insertReservation(row);
        } catch (DuplicateKeyException exception) {
            if (ReservationConstraintViolations.isActiveReservationDuplicate(exception)) {
                throw new CommonException(ErrorCode.DUPLICATED_RESERVATION, exception);
            }
            throw exception;
        }

        reservationMapper.insertCreatedHistory(row.getReservationId(), userId, status);
        // 현장예매도 사전예약과 같은 규칙으로 동반 반려동물을 받는다 - 방문 통계에서 현장 관람객이
        // 빠지면 알레르기·품종 분포가 사전예약 쪽으로 치우친다.
        reservationPetService.attachPets(
                row.getReservationId(),
                userId,
                context.isPetAllowed(),
                request == null ? null : request.petIds()
        );
        String entryQrToken = paymentRequired ? null : entryQrService.issueForReservation(row.getReservationId());

        // 실시간 예약현황 확인용
        eventPublisher.publishEvent(new ReservationStatusChangedEvent(fairId));

        return new CreateOnsiteReservationResponse(
                row.getReservationId(),
                reservationNo,
                ReservationType.ONSITE_DIRECT.name(),
                today,
                status,
                amount,
                paymentRequired,
                paymentExpiresAt,
                entryQrToken
        );
    }

    private void validateIdentifiers(Long fairId, Long userId) {
        if (fairId == null || fairId <= 0 || userId == null || userId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateFairAndSales(OnsiteReservationCreationContext context, LocalDateTime now) {
        if (context.getPublishedAt() == null
                || context.getCanceledAt() != null
                || !("PREPARING".equals(context.getFairStatus())
                || "IN_PROGRESS".equals(context.getFairStatus()))) {
            throw new CommonException(ErrorCode.ONSITE_RESERVATION_CLOSED);
        }

        OnsiteSalesStatus salesStatus = OnsiteSalesStatus.from(context.getOnsiteSalesStatus())
                .orElse(OnsiteSalesStatus.CLOSED);
        if (salesStatus == OnsiteSalesStatus.PAUSED) {
            throw new CommonException(ErrorCode.ONSITE_RESERVATION_PAUSED);
        }
        if (salesStatus != OnsiteSalesStatus.OPEN) {
            throw new CommonException(ErrorCode.ONSITE_RESERVATION_CLOSED);
        }
        if (context.getOnsitePrice() == null || context.getOnsitePrice() < 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        LocalTime entryStartTime = context.getEntryStartTime();
        LocalTime entryEndTime = context.getEntryEndTime();
        if (context.getOperationDate() == null
                || entryStartTime == null
                || entryEndTime == null
                || entryEndTime.isBefore(entryStartTime)) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        LocalDateTime entryEndsAt = LocalDateTime.of(context.getOperationDate(), entryEndTime);
        if (now.isAfter(entryEndsAt)) {
            throw new CommonException(ErrorCode.ONSITE_RESERVATION_CLOSED);
        }
        if (context.getOnsitePrice() > 0 && now.plusMinutes(PAYMENT_WAIT_MINUTES).isAfter(entryEndsAt)) {
            throw new CommonException(ErrorCode.ONSITE_RESERVATION_CLOSED);
        }
    }

    private void validateUser(ReservationUserSnapshot user) {
        if (user == null) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
        // 역할은 보지 않는다 - 관리자·사업자 계정도 개인 자격으로는 관람객이라 예약할 수 있다.
        // 로그인만 되어 있으면 통과시키고, 정지·탈퇴 계정만 막는다.
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void validateTerms(boolean paymentRequired, CreateOnsiteReservationRequest request) {
        if (!paymentRequired) {
            return;
        }
        if (request == null
                || !Boolean.TRUE.equals(request.reservationTermsAgreed())
                || !ONSITE_TERMS_VERSION.equals(request.reservationTermsVersion())) {
            throw new CommonException(ErrorCode.RESERVATION_TERMS_REQUIRED);
        }
    }
}
