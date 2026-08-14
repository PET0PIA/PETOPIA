package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.CreateReservationRequest;
import com.ms.petopia.api.reservation.dto.CreateReservationResponse;
import com.ms.petopia.api.reservation.dto.ReservationCreationContext;
import com.ms.petopia.api.reservation.dto.ReservationInsertRow;
import com.ms.petopia.api.reservation.dto.ReservationUserSnapshot;
import com.ms.petopia.api.reservation.mapper.ReservationCapacityMapper;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
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

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String CONFIRMED = "CONFIRMED";
    private static final long PAYMENT_WAIT_MINUTES = 10;

    private final ReservationMapper reservationMapper;
    private final ReservationCapacityMapper capacityMapper;
    private final ReservationNumberGenerator reservationNumberGenerator;
    private final ReservationTimeProvider timeProvider;
    private final EntryQrService entryQrService;

    // 실시간 예약현황 이벤트 발행
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 본인 1인 1매 예약을 생성한다.
     *
     * <p>유료 예약은 결제 대기 상태까지만 만든다. 결제 준비·승인과 QR 발급은
     * 결제/입장 도메인의 후속 단계에서 처리한다.
     *
     * <p><b>정원 판정</b>은 {@link ReservationCapacityMapper#occupy}의 조건부 UPDATE가 단독으로
     * 책임진다. 검증을 전부 마친 뒤 마지막에 호출하는 순서가 중요하다 — 점유에 성공한 뒤 검증에서
     * 떨어지면 그 사이 다른 사람이 못 사는 좌석이 생기기 때문이다. 트랜잭션이 어떤 이유로든
     * 롤백되면 점유도 함께 롤백되므로 별도 보상 처리는 필요 없다.
     */
    @Transactional
    public CreateReservationResponse create(
            Long fairId,
            Long userId,
            CreateReservationRequest request
    ) {
        validateIdentifiersAndRequest(fairId, userId, request);

        ReservationCreationContext context =
                reservationMapper.selectCreationContext(fairId, request.visitDate());
        if (context == null) {
            if (!reservationMapper.existsFair(fairId)) {
                throw new CommonException(ErrorCode.RESERVATION_FAIR_NOT_FOUND);
            }
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }

        LocalDate today = timeProvider.today();
        validateFair(context, today);

        if (reservationMapper.existsActiveReservation(fairId, userId)) {
            throw new CommonException(ErrorCode.DUPLICATED_RESERVATION);
        }

        ReservationUserSnapshot user = reservationMapper.selectUserSnapshot(userId);
        validateUser(user);

        boolean paymentRequired = context.getReservationFee() > 0;
        validateTerms(paymentRequired, request);

        // 정원 판정. 여기부터 커밋까지가 임계구간이다 - 조건부 UPDATE가 잡는 fair_dates 행
        // 잠금 하나뿐이고, 위의 검증들은 전부 잠금 밖에서 끝났다.
        //
        // 미리 세어보고 분기하지 않는다. 조회와 차감 사이에 창이 생겨 정원이 초과된다.
        // 영향 행수 0이 곧 매진이다.
        if (capacityMapper.occupy(fairId, request.visitDate()) != 1) {
            throw new CommonException(ErrorCode.RESERVATION_SOLD_OUT);
        }

        LocalDateTime now = timeProvider.now();
        String status = paymentRequired ? PENDING_PAYMENT : CONFIRMED;
        LocalDateTime paymentExpiresAt = paymentRequired ? now.plusMinutes(PAYMENT_WAIT_MINUTES) : null;
        String reservationNo = reservationNumberGenerator.generate(today);

        ReservationInsertRow row = ReservationInsertRow.builder()
                .reservationNo(reservationNo)
                .fairId(fairId)
                .userId(userId)
                .visitDate(request.visitDate())
                .reservationType(ReservationType.ADVANCE.name())
                .status(status)
                .reservationAmount(context.getReservationFee())
                .reserverName(user.getNickname())
                .reserverPhone(user.getPhone())
                .reserverEmail(user.getEmail())
                .channel("ONLINE")
                .agreedTerms(paymentRequired)
                .reservationTermsVersion(paymentRequired ? request.reservationTermsVersion().trim() : null)
                .reservationTermsAgreedAt(paymentRequired ? now : null)
                .reservedAt(paymentRequired ? null : now)
                .paymentExpiresAt(paymentExpiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            reservationMapper.insertReservation(row);
        } catch (DuplicateKeyException e) {
            if (ReservationConstraintViolations.isActiveReservationDuplicate(e)) {
                throw new CommonException(ErrorCode.DUPLICATED_RESERVATION, e);
            }
            throw e;
        }

        reservationMapper.insertCreatedHistory(row.getReservationId(), userId, status);
        String entryQrToken = paymentRequired ? null : entryQrService.issueForReservation(row.getReservationId());

        // 실시간 예약 현황용
        eventPublisher.publishEvent(new ReservationStatusChangedEvent(fairId));

        return new CreateReservationResponse(
                row.getReservationId(),
                reservationNo,
                ReservationType.ADVANCE.name(),
                status,
                context.getReservationFee(),
                paymentRequired,
                paymentExpiresAt,
                entryQrToken
        );
    }

    private void validateIdentifiersAndRequest(
            Long fairId,
            Long userId,
            CreateReservationRequest request
    ) {
        if (fairId == null || fairId <= 0 || userId == null || userId <= 0
                || request == null || request.visitDate() == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateFair(ReservationCreationContext context, LocalDate today) {
        if (context.getPublishedAt() == null
                || context.getCanceledAt() != null
                || context.getReservationStartDate() == null
                || context.getReservationEndDate() == null
                || today.isBefore(context.getReservationStartDate())
                || today.isAfter(context.getReservationEndDate())) {
            throw new CommonException(ErrorCode.RESERVATION_NOT_OPEN);
        }

        if (!context.getOperationDate().isAfter(today)) {
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }

        if (context.getReservationFee() < 0 || context.getCapacity() <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateUser(ReservationUserSnapshot user) {
        if (user == null) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
        if (!"ACTIVE".equals(user.getStatus()) || !"USER".equals(user.getRole())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void validateTerms(boolean paymentRequired, CreateReservationRequest request) {
        if (!paymentRequired) {
            return;
        }

        if (!Boolean.TRUE.equals(request.reservationTermsAgreed())
                || request.reservationTermsVersion() == null
                || request.reservationTermsVersion().isBlank()) {
            throw new CommonException(ErrorCode.RESERVATION_TERMS_REQUIRED);
        }
    }
}
