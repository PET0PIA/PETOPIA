package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.CreateFairCancelRequestRequest;
import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.FairCancelRequest;
import com.ms.petopia.api.fair.dto.FairCancelRequestResponse;
import com.ms.petopia.api.fair.dto.FairCancelRequestStatus;
import com.ms.petopia.api.fair.dto.FairReviewDecision;
import com.ms.petopia.api.fair.dto.FairStatus;
import com.ms.petopia.api.fair.dto.ReviewFairCancelRequestRequest;
import com.ms.petopia.api.fair.dto.ReviewFairCancelRequestResponse;
import com.ms.petopia.api.fair.mapper.FairCancelRequestMapper;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 행사 취소 신청/심사(fair_cancel_requests) 처리.
 *
 * <p>승인 시 이 테이블의 status만 바뀌는 게 아니라 {@code fairs.canceled_at}도 함께 채운다.
 * reservation 도메인({@code ReservationAvailabilityService.validateReservableFair})과
 * recruitnotice 도메인은 이미 {@code fairs.canceled_at IS NOT NULL}을 취소 신호로 보고
 * 있고, refund 도메인도 {@code RefundReason.FAIR_CANCEL_USER}/{@code FAIR_CANCEL_VENDOR}로
 * 이 값을 조회해서 환불을 처리할 예정이다 - 따라서 이 필드를 실제로 채우는 것이 이 작업의
 * 핵심이다. 승인/반려를 다른 도메인에 이벤트로 알리는 것은 이번 범위에 없다(도메인 간 이벤트
 * 발행 구조가 아직 없어서, 각 도메인이 fairs.canceled_at을 직접 조회해서 확인하는 것을 전제로 한다).
 */
@Service
@RequiredArgsConstructor
public class FairCancelRequestService {

    /**
     * 취소를 신청할 수 있는 상태. 심사 전(RECEIVED)이거나 이미 끝난 상태(REJECTED/EXPIRED/ENDED)는
     * 취소할 대상 자체가 없다. publish 가능 상태(FairService.PUBLISHABLE_STATUSES)와 동일한
     * 집합이다 - "실제로 진행 중인 행사"라는 같은 개념을 가리키기 때문.
     */
    private static final Set<FairStatus> CANCELABLE_STATUSES =
            EnumSet.of(FairStatus.PAYMENT_PENDING, FairStatus.PREPARING, FairStatus.IN_PROGRESS);

    private final FairCancelRequestMapper cancelRequestMapper;
    private final FairMapper fairMapper;
    private final FairTimeProvider timeProvider;

    /**
     * 취소를 신청한다. PENDING 상태로 등록되고, SUPER_ADMIN의 검토를 기다린다.
     */
    @Transactional
    public FairCancelRequestResponse create(Long fairId, Long requestedBy, CreateFairCancelRequestRequest request) {
        if (requestedBy == null || requestedBy <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new CommonException(ErrorCode.FAIR_CANCEL_REASON_REQUIRED);
        }

        Fair fair = findFairOrThrow(fairId);
        if (fair.getCanceledAt() != null || !CANCELABLE_STATUSES.contains(fair.getStatus())) {
            throw new CommonException(ErrorCode.FAIR_CANCEL_NOT_REQUESTABLE);
        }
        if (cancelRequestMapper.selectPendingByFairId(fairId) != null) {
            throw new CommonException(ErrorCode.FAIR_CANCEL_NOT_REQUESTABLE);
        }

        FairCancelRequest cancelRequest = new FairCancelRequest();
        cancelRequest.setFairId(fairId);
        cancelRequest.setRequestedBy(requestedBy);
        cancelRequest.setReason(request.reason().trim());
        cancelRequest.setCreatedAt(timeProvider.now());

        cancelRequestMapper.insert(cancelRequest);
        return toResponse(cancelRequest);
    }

    /**
     * 특정 행사의 취소 신청 이력을 최신순으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<FairCancelRequestResponse> getCancelRequests(Long fairId) {
        findFairOrThrow(fairId);
        return cancelRequestMapper.selectByFairId(fairId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 취소 신청을 승인하거나 반려한다. PENDING 상태의 신청만 검토할 수 있다.
     * 승인 시 fairs.canceled_at을 채운다 - fairs.status는 바꾸지 않는다(취소는 상태값이 아니라
     * 플래그로 관리한다는 기존 설계, {@link FairStatus} javadoc 참고).
     */
    @Transactional
    public ReviewFairCancelRequestResponse review(
            Long fairId, Long cancelRequestId, Long reviewerId, ReviewFairCancelRequestRequest request
    ) {
        if (reviewerId == null || reviewerId <= 0 || request == null || request.decision() == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        FairCancelRequest cancelRequest = findCancelRequestInFair(fairId, cancelRequestId);
        if (cancelRequest.getStatus() != FairCancelRequestStatus.PENDING) {
            throw new CommonException(ErrorCode.FAIR_CANCEL_REQUEST_NOT_PENDING);
        }
        boolean approved = request.decision() == FairReviewDecision.APPROVE;
        if (!approved && (request.rejectReason() == null || request.rejectReason().isBlank())) {
            throw new CommonException(ErrorCode.FAIR_CANCEL_REJECT_REASON_REQUIRED);
        }

        LocalDateTime now = timeProvider.now();

        FairCancelRequest update = new FairCancelRequest();
        update.setFairCancelRequestId(cancelRequestId);
        update.setReviewedBy(reviewerId);
        update.setReviewedAt(now);
        if (approved) {
            update.setStatus(FairCancelRequestStatus.APPROVED);
        } else {
            update.setStatus(FairCancelRequestStatus.REJECTED);
            update.setRejectReason(request.rejectReason().trim());
        }
        cancelRequestMapper.update(update);

        LocalDateTime canceledAt = null;
        if (approved) {
            canceledAt = now;
            Fair fairUpdate = new Fair();
            fairUpdate.setFairId(fairId);
            fairUpdate.setCanceledAt(now);
            fairMapper.update(fairUpdate);
        }

        return new ReviewFairCancelRequestResponse(
                cancelRequestId,
                fairId,
                update.getStatus().name(),
                now,
                update.getRejectReason(),
                canceledAt
        );
    }

    private Fair findFairOrThrow(Long fairId) {
        if (fairId == null || fairId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Fair fair = fairMapper.selectById(fairId);
        if (fair == null) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }
        return fair;
    }

    /**
     * cancelRequestId가 fairId 소속인지 함께 검증한다. 다른 행사의 취소 신청이면 존재하지
     * 않는 것과 동일하게 404로 응답한다(다른 행사 소속 여부를 알려주지 않기 위함).
     */
    private FairCancelRequest findCancelRequestInFair(Long fairId, Long cancelRequestId) {
        if (fairId == null || fairId <= 0 || cancelRequestId == null || cancelRequestId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        FairCancelRequest cancelRequest = cancelRequestMapper.selectById(cancelRequestId);
        if (cancelRequest == null || !cancelRequest.getFairId().equals(fairId)) {
            throw new CommonException(ErrorCode.FAIR_CANCEL_REQUEST_NOT_FOUND);
        }
        return cancelRequest;
    }

    private FairCancelRequestResponse toResponse(FairCancelRequest cancelRequest) {
        return new FairCancelRequestResponse(
                cancelRequest.getFairCancelRequestId(),
                cancelRequest.getFairId(),
                cancelRequest.getRequestedBy(),
                cancelRequest.getReason(),
                cancelRequest.getStatus() == null ? FairCancelRequestStatus.PENDING.name() : cancelRequest.getStatus().name(),
                cancelRequest.getRejectReason(),
                cancelRequest.getReviewedBy(),
                cancelRequest.getReviewedAt(),
                cancelRequest.getCreatedAt()
        );
    }
}
