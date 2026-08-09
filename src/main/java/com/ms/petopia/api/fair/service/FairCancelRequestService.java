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
import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
    private final AuditLogService auditLogService;
    private final FairAdminAccessGuard fairAdminAccessGuard;

    /**
     * 취소를 신청한다. PENDING 상태로 등록되고, SUPER_ADMIN의 검토를 기다린다.
     *
     * <p>{@code selectPendingByFairId} 확인 후 insert하는 방식이라 애플리케이션 레벨의
     * 체크만으로는 두 요청이 동시에 들어오면 둘 다 통과해 PENDING이 중복 생성될 수 있다.
     * 그래서 이 체크는 "흔한 경우를 빨리 걸러 불필요한 제약 위반 예외를 피하는" 용도로만
     * 남겨두고, 실제 방어는 {@code fair_cancel_requests.pending_key} 부분 유니크 제약
     * (V11 마이그레이션, {@code application.active_key}와 동일한 패턴)이 DB 레벨에서
     * 맡는다. 그 제약을 위반하면(동시에 들어온 다른 요청이 먼저 커밋됐으면)
     * {@link DuplicateKeyException}을 잡아 동일한 에러 코드로 변환한다.
     *
     * <p>SecurityConfig는 EVENT_ADMIN role만 확인하고 "그 행사 담당자인지"는 못 가린다
     * (코드래빗 지적 - role만 있으면 다른 행사 EVENT_ADMIN도 신청할 수 있었음) - 그래서
     * {@link FairAdminAccessGuard}로 여기서 한 번 더 확인한다.
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
        fairAdminAccessGuard.checkAssigned(fairId);
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

        try {
            cancelRequestMapper.insert(cancelRequest);
        } catch (DuplicateKeyException e) {
            throw new CommonException(ErrorCode.FAIR_CANCEL_NOT_REQUESTABLE);
        }
        return toResponse(cancelRequest);
    }

    /**
     * 특정 행사의 취소 신청 이력을 최신순으로 조회한다. requestedBy/reason/rejectReason처럼
     * 그 행사 내부 사정이 담기므로, SecurityConfig의 role 검증(EVENT_ADMIN/SUPER_ADMIN)만으로는
     * 부족하다 - 다른 행사 EVENT_ADMIN이 이 API로 남의 행사 이력을 볼 수 있었던 문제(코드래빗
     * 지적)를 막기 위해 {@link FairAdminAccessGuard}로 담당 행사인지 한 번 더 확인한다.
     */
    @Transactional(readOnly = true)
    public List<FairCancelRequestResponse> getCancelRequests(Long fairId) {
        findFairOrThrow(fairId);
        fairAdminAccessGuard.checkAssigned(fairId);
        return cancelRequestMapper.selectByFairId(fairId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 취소 신청을 승인하거나 반려한다. PENDING 상태의 신청만 검토할 수 있다.
     * 승인 시 fairs.canceled_at을 채운다 - fairs.status는 바꾸지 않는다(취소는 상태값이 아니라
     * 플래그로 관리한다는 기존 설계, {@link FairStatus} javadoc 참고).
     *
     * <p>PENDING 여부는 미리 SELECT로 확인하지 않고 UPDATE의 WHERE 절이 직접 검증한다
     * ({@link com.ms.petopia.api.fair.mapper.FairCancelRequestMapper#update} 참고).
     * "확인 후 갱신" 순서로 하면 두 검토 요청이 동시에 PENDING을 읽어 둘 다 통과해버릴 수
     * 있는데, 조건부 UPDATE는 그 경합을 DB가 원자적으로 해소하게 해서
     * 둘 중 먼저 커밋된 하나만 실제로 반영되고 나머지는 영향 행 0건으로 실패한다.
     */
    @Transactional
    public ReviewFairCancelRequestResponse review(
            Long fairId, Long cancelRequestId, Long reviewerId, ReviewFairCancelRequestRequest request
    ) {
        if (reviewerId == null || reviewerId <= 0 || request == null || request.decision() == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        // fairId 소속 여부(404) 확인용. 존재 자체는 레이스가 없는 값이라 미리 조회해도 안전하다 -
        // 상태(PENDING) 판단만 아래 조건부 UPDATE로 넘긴다.
        findCancelRequestInFair(fairId, cancelRequestId);
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
        int updated = cancelRequestMapper.update(update);
        if (updated == 0) {
            throw new CommonException(ErrorCode.FAIR_CANCEL_REQUEST_NOT_PENDING);
        }

        LocalDateTime canceledAt = null;
        if (approved) {
            canceledAt = now;
            Fair fairUpdate = new Fair();
            fairUpdate.setFairId(fairId);
            fairUpdate.setCanceledAt(now);
            // fair_cancel_requests는 이미 APPROVED로 갱신된 뒤라, 여기서 실패하면(정상 흐름에선
            // 거의 일어나지 않지만 - fairs는 하드삭제하지 않음) 신청 상태와 fairs.canceled_at이
            // 어긋난 채로 감사 로그만 "성공"으로 남을 수 있다. 결과를 확인해 즉시 롤백한다.
            if (fairMapper.update(fairUpdate) != 1) {
                throw new CommonException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            // reviewerId가 실제 SUPER_ADMIN인지는 SecurityConfig가 이 엔드포인트 진입 전에
            // role로 이미 검증했다(FairCancelRequestController 참고). actorRole은 그 전제로 고정값을 쓴다.
            auditLogService.record(
                    reviewerId,
                    ActorType.ADMIN,
                    "SUPER_ADMIN",
                    ActionType.FAIR_CANCEL_APPROVE,
                    TargetType.FAIR,
                    fairId,
                    null,
                    Map.of("fairCancelRequestId", cancelRequestId, "canceledAt", canceledAt)
            );
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
