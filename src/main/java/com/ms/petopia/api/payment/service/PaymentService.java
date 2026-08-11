package com.ms.petopia.api.payment.service;

import com.ms.petopia.api.application.service.ApplicationService;
import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.payment.client.ReservationPaymentContractClient;
import com.ms.petopia.api.payment.client.TossPaymentClient;
import com.ms.petopia.api.payment.dto.*;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.refund.dto.RefundReason;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RequestedByDomain;
import com.ms.petopia.api.refund.service.RefundService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 결제 조회를 담당하는 서비스.
 *
 * <p>결제 생성(예약금/참가비/개설비 결제 처리)은 별도 API로 분리될 예정이라 이 클래스에는
 * 아직 조회 기능만 둔다.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final TossPaymentClient tossPaymentClient;
    private final ReservationPaymentContractClient reservationPaymentContractClient;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ApplicationService applicationService;
    private final RefundService refundService;

    // RefundService.refund()의 actingUserId는 원래 "누가 환불을 처리했는지" 기록하는 값인데,
    // 여기서는 사람이 아니라 시스템(이 메서드)이 자동으로 트리거하는 환불이라 실제 유저 ID가 없다.
    // "시스템이 처리했다"는 의미의 더미 값으로 0L을 쓴다.
    private static final Long SYSTEM_ACTOR_USER_ID = 0L;

    /**
     * 결제 ID로 상세 조회한다.
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_NOT_FOUND} 존재하지 않는 결제 ID일 때
     */
    public PaymentResponse getPayment(Long paymentId) {
        PaymentRow row = paymentMapper.selectById(paymentId);
        if (row == null) {
            throw new CommonException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return PaymentResponse.from(row);
    }

    /**
     * 예약 ID로 그 예약의 예약금 결제를 조회한다. 예약 도메인이 취소 처리 중 환불 API를
     * 부르기 전에 paymentId를 알아내는 용도(동기 호출 흐름: 예약취소 -> 이 API로 paymentId
     * 조회 -> 환불 API 호출 -> 응답 받아서 예약 상태 전환).
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_NOT_FOUND} 그 예약으로 결제된 적이 없을 때
     */
    public PaymentResponse getByReservationId(Long reservationId) {
        PaymentRow row = paymentMapper.selectByReservationId(reservationId);
        if (row == null) {
            throw new CommonException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return PaymentResponse.from(row);
    }

    /**
     * 참가신청 ID로 그 신청의 참가비 결제를 조회한다. 채린님(참가업체) 도메인이 참가 취소승인
     * 처리 중 환불 대상 paymentId를 찾는 용도. 취소승인은 결제 전(신청만 하고 아직 결제를
     * 시작 안 한 상태)에도 가능하다고 확인됐으므로, {@link #getByReservationId}와 달리
     * 예외 대신 null을 반환한다 — 호출부가 null이면 환불 호출 자체를 건너뛰어야 한다.
     */
    public PaymentResponse findByApplicationId(Long applicationId) {
        PaymentRow row = paymentMapper.selectByApplicationId(applicationId);
        return row == null ? null : PaymentResponse.from(row);
    }

    /**
     * 참가비 결제를 생성한다. application 테이블은 조회하지 않으므로(애그리거트 간
     * ID 참조 원칙 유지) 금액·소속 정보는 호출자가 요청에 실어보낸 값을 그대로 신뢰한다.
     *
     * <p>동일 참가신청에 대한 중복 결제는 idempotencyKey(UK_PAYMENT_IDEMPOTENCY_KEY)로
     * DB가 막는다 — 여기서 잡아 {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE}로 변환한다.
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} 이미 결제된 참가신청일 때
     */

    @Transactional
    public PaymentResponse payVendorFee(Long applicationId,Long userId, VendorFeePaymentRequest request) {
        String idempotencyKey = "VENDOR_FEE_" + applicationId;
        PaymentRow row = createOrRetryPayment(idempotencyKey, request.amount(), userId, () -> {
            LocalDateTime now = LocalDateTime.now();
            PaymentRow newRow = new PaymentRow();
            newRow.setPaymentType("VENDOR_FEE");
            newRow.setAmount(request.amount());
            newRow.setStatus("PENDING");
            newRow.setMethod("TOSS");
            newRow.setIdempotencyKey(idempotencyKey);
            newRow.setCreatedAt(now);
            newRow.setUpdatedAt(now);
            newRow.setFairId(request.fairId());
            newRow.setBusinessId(request.businessId());
            newRow.setPayerUserId(userId);
            newRow.setApplicationId(applicationId);
            return newRow;
        });

        return PaymentResponse.from(row);
    }

    /**
     * 예약금 결제를 생성한다. 참가비와 달리 금액을 클라이언트가 안 보내도 된다 —
     * 예약 도메인의 결제 컨텍스트 조회로 진짜 금액을 받아온다.
     *
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 예약 소유자가 아닐 때
     * @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} 예약이 결제 가능한 상태가 아닐 때
     */
    public PaymentResponse payReservationDeposit(Long reservationId, Long userId) {
        ReservationPaymentContext context = reservationPaymentContractClient.getPaymentContext(reservationId);
        if (!userId.equals(context.payerUserId())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }

        String idempotencyKey = "RESERVATION_DEPOSIT_" + reservationId;
        PaymentRow row = createOrRetryPayment(idempotencyKey, context.amount(), userId, () -> {
            LocalDateTime now = LocalDateTime.now();
            PaymentRow newRow = new PaymentRow();
            newRow.setPaymentType("RESERVATION_DEPOSIT");
            newRow.setAmount(context.amount());
            newRow.setStatus("PENDING");
            newRow.setMethod("TOSS");
            newRow.setIdempotencyKey(idempotencyKey);
            newRow.setCreatedAt(now);
            newRow.setUpdatedAt(now);
            newRow.setFairId(context.fairId());
            newRow.setPayerUserId(userId);
            newRow.setReservationId(reservationId);
            return newRow;
        });

        return PaymentResponse.from(row);
    }

    /**
     * 행사개설비 결제를 생성한다. 참가비와 동일 구조로 fair 테이블은 조회하지 않으므로(애그리거트
     * 간 ID 참조 원칙 유지) 금액은 호출자가 요청에 실어보낸 값을 그대로 신뢰한다.
     * fairId만 채워지고 businessId·reservationId·applicationId는 전부 null.
     *
     * <p>결제 완료 후 행사 상태를 "준비중"으로 전이하는 건 이 메서드 책임이 아니다 — 행사 도메인이
     * 결제 완료를 어떻게 감지할지(폴링/이벤트 발행) 아직 미정이라 API 명세서에 "미확정"으로
     * 남아있다. 지금은 결제 자체만 처리하고 크로스도메인 통지는 하지 않는다(참가비와 동일).
     *
     * <p>동일 행사에 대한 중복 결제는 idempotencyKey(UK_PAYMENT_IDEMPOTENCY_KEY)로
     * DB가 막는다 — 여기서 잡아 {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE}로 변환한다.
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} 이미 결제된 행사일 때
     */
    @Transactional
    public PaymentResponse payFairOpeningFee(Long fairId, Long userId, OpeningFeePaymentRequest request) {
        String idempotencyKey = "FAIR_OPENING_FEE_" + fairId;
        PaymentRow row = createOrRetryPayment(idempotencyKey, request.amount(), userId, () -> {
            LocalDateTime now = LocalDateTime.now();
            PaymentRow newRow = new PaymentRow();
            newRow.setPaymentType("FAIR_OPENING_FEE");
            newRow.setAmount(request.amount());
            newRow.setStatus("PENDING");
            newRow.setMethod("TOSS");
            newRow.setIdempotencyKey(idempotencyKey);
            newRow.setCreatedAt(now);
            newRow.setUpdatedAt(now);
            newRow.setFairId(fairId);
            newRow.setPayerUserId(userId);
            return newRow;
        });

        return PaymentResponse.from(row);
    }

    /**
     * 결제 생성(pay*) 3종이 공유하는 공통 로직 — 결제 3종 공통 후속과제(2026-08-06 CodeRabbit
     * 지적, 실패 결제 재시도 불가) 해결.
     *
     * <p>동일 idempotencyKey로 이전 시도가 있었는지 먼저 확인해서:
     * <ul>
     *   <li>없으면 새로 insert(기존과 동일, 동시 첫 시도 경쟁은 DuplicateKeyException으로 처리)</li>
     *   <li>FAILED로 남아있고 요청자가 그 결제의 원래 결제자면, 그 행을 PENDING으로 되돌려 재사용(재결제 허용)</li>
     *   <li>FAILED로 남아있지만 요청자가 원래 결제자가 아니면 ACCESS_DENIED(남의 결제 재시도 금지)</li>
     *   <li>PENDING/PROCESSING/COMPLETED면 여전히 중복결제로 막음(기존 동작 유지)</li>
     * </ul>
     *
     * @param idempotencyKey 원업무 식별자 기준 키(예: {@code "VENDOR_FEE_" + applicationId})
     * @param amount 이번 시도의 결제 금액 — 재사용 시에도 이 값으로 갱신한다(재시도 시점에
     *               금액이 달라질 수 있어서, 예: 참가비 재승인 등)
     * @param userId 재시도를 요청한 사용자. 기존 FAILED 행의 payerUserId와 다르면 남의 결제를
     *               멋대로 PENDING으로 되돌리는 셈이라 막는다(CodeRabbit 지적, PR #63).
     * @param newRowSupplier 이전 시도가 아예 없을 때 삽입할 새 PaymentRow를 만드는 함수
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 기존 FAILED 결제의 결제자가 아닐 때
     * @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} 이미 결제 진행/완료 중이거나,
     *         다른 요청이 먼저 재시도를 선점했을 때
     */
    private PaymentRow createOrRetryPayment(
            String idempotencyKey, Long amount, Long userId, Supplier<PaymentRow> newRowSupplier
    ) {
        PaymentRow existing = paymentMapper.selectByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            if (!"FAILED".equals(existing.getStatus())) {
                throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
            }
            if (!userId.equals(existing.getPayerUserId())) {
                throw new CommonException(ErrorCode.ACCESS_DENIED);
            }

            LocalDateTime now = LocalDateTime.now();
            int reset = paymentMapper.resetFailedToPending(existing.getPaymentId(), amount, now);
            if (reset == 0) {
                // 우리가 조회한 뒤, 다른 요청이 먼저 재시도를 선점했거나 상태가 바뀐 경우 — 충돌로 처리.
                throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
            }

            existing.setStatus("PENDING");
            existing.setAmount(amount);
            existing.setUpdatedAt(now);
            existing.setTossPaymentKey(null);
            existing.setPaidAt(null);
            return existing;
        }

        PaymentRow row = newRowSupplier.get();
        try {
            paymentMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // selectByIdempotencyKey로는 못 찾았는데 그 사이 다른 요청이 먼저 insert에 성공한
            // 경우(첫 결제 동시요청 경쟁) — 기존과 동일하게 충돌 처리.
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }
        return row;
    }

    /**
     * 조건별 결제 목록 조회(관리자용). fairId·businessId·paymentType·status 전부 선택적이고
     * 넘긴 값들은 AND로 조합된다.
     *
     * @throws CommonException {@link ErrorCode#INVALID_INPUT_VALUE} page·size가 범위를 벗어났을 때
     */
    public PaymentListResponse getPayments(
            Long fairId, Long businessId, String paymentType, String status, int page, int size
    ) {
        validatePageAndSize(page, size);
        long offset = (long) page * size;
        List<PaymentRow> rows = paymentMapper.selectByFilter(fairId, businessId, paymentType, status, null, offset, size);
        long total = paymentMapper.countByFilter(fairId, businessId, paymentType, status, null);
        return PaymentListResponse.of(rows, page, size, total);
    }

    /**
     * 로그인 사용자 본인의 결제 내역 조회(마이페이지). 다른 필터 없이 payerUserId만 건다.
     *
     * @throws CommonException {@link ErrorCode#INVALID_INPUT_VALUE} page·size가 범위를 벗어났을 때
     */
    public PaymentListResponse getMyPayments(Long userId, int page, int size) {
        validatePageAndSize(page, size);
        long offset = (long) page * size;
        List<PaymentRow> rows = paymentMapper.selectByFilter(null, null, null, null, userId, offset, size);
        long total = paymentMapper.countByFilter(null, null, null, null, userId);
        return PaymentListResponse.of(rows, page, size, total);
    }

    /**
     * 컨트롤러의 {@code @Min}/{@code @Max} 어노테이션은 여기서 검증을 대신하지 않는다 —
     * 이 프로젝트가 쓰는 {@code standaloneSetup} 기반 컨트롤러 테스트에서 메서드 파라미터
     * 검증이 실제로 안 걸리는 걸 확인해서(CodeRabbit 리뷰 지적, PR #62), 프레임워크 동작에
     * 기대지 않고 서비스 계층에서 명시적으로 막는다. 특히 page < 0이면 SQL의
     * {@code OFFSET}이 음수가 돼서 DB 에러로 이어질 수 있어 이 검증이 실질적으로도 중요하다.
     */
    private void validatePageAndSize(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }


    // 이 메서드는 일부러 @Transactional을 안 붙인다. markProcessing()의 커밋이
    // 토스를 부르기 "전에" 완전히 끝나서 다른 동시 요청 눈에 즉시 보여야 하기 때문 —
    // 하나의 트랜잭션으로 묶어버리면 markProcessing 변경이 메서드가 끝날 때까지
    // 커밋 안 되고, 그 사이 다른 요청도 여전히 PENDING을 보고 똑같이 토스를 불러버릴
    // 수 있다. 각 markXxx 호출은 UPDATE 한 줄짜리라 그 자체로 원자적이라
    // 트랜잭션으로 묶지 않아도 개별 쓰기의 정합성은 깨지지 않는다.
    public PaymentResponse confirmPayment(Long paymentId, Long userId, ConfirmPaymentRequest request) {
        PaymentRow row = paymentMapper.selectById(paymentId);
        if (row == null) {
            throw new CommonException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (!userId.equals(row.getPayerUserId())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
        if (!"PENDING".equals(row.getStatus())) {
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        // 카드 승인을 시도하기 전에 신청서가 여전히 결제 가능한 상태(PAYMENT_PENDING)인지
        // 먼저 확인한다. 이미 취소된 신청이면 여기서 막아서 불필요한 선점(PROCESSING)과
        // 카드 승인 자체를 방지한다.
        if ("VENDOR_FEE".equals(row.getPaymentType())) {
            applicationService.assertPayable(row.getApplicationId());
        }

        // 토스를 부르기 전에 먼저 선점한다. 동시에 두 요청이 여기 도달해도 이 UPDATE는
        // 원자적이라 딱 하나만 1을 받는다 — 선점 실패(0)면 토스 호출 자체를 안 하고 끝낸다.
        int claimed = paymentMapper.markProcessing(paymentId, LocalDateTime.now());
        if (claimed == 0) {
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        String orderId = "PAYMENT_" + row.getPaymentId();
        TossPaymentResponse tossResponse;
        try {
            tossResponse = tossPaymentClient.confirmPayment(request.paymentKey(), orderId, row.getAmount());
        } catch (CommonException e) {
            // 토스가 확정적으로 거부한 경우(4xx)만 FAILED로 남긴다. 5xx(게이트웨이 장애)는
            // 실제로는 승인이 처리됐을 수도 있어 여기서 실패로 단정하지 않고 PROCESSING
            // 그대로 둔다 — 나중에 상태조회로 확인 후 재시도하는 흐름은 별도 구현 필요.
            if (e.getErrorCode() == ErrorCode.PAYMENT_APPROVAL_FAILED) {
                paymentMapper.markFailed(paymentId, LocalDateTime.now());
            }
            throw e;
        }
        LocalDateTime now = LocalDateTime.now();
        row.setStatus("COMPLETED");
        row.setMethod(tossResponse.method());
        row.setTossPaymentKey(tossResponse.paymentKey());
        row.setPaidAt(now);
        row.setUpdatedAt(now);

        int updated = paymentMapper.markCompleted(row);
        if (updated == 0) {
            // markProcessing으로 선점에 성공한 요청만 여기 도달하므로 이론상 발생하지
            // 않아야 하지만, 방어적으로 남겨둔다.
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        if ("RESERVATION_DEPOSIT".equals(row.getPaymentType())) {
            notifyReservationDomain(row);
        }

        // 참가비 결제 완료를 참가업체 도메인에 통지해 신청 상태를 PAYMENT_PENDING -> CONFIRMED로
        // 전환시키고 부스를 자동 생성시킨다.
        if ("VENDOR_FEE".equals(row.getPaymentType())) {
            try {
                applicationService.confirmVendorPayment(row.getApplicationId(), row.getPaymentId(), row.getAmount());
            } catch (CommonException e) {
                // 카드 승인은 이미 끝나 결제는 COMPLETED로 확정됐으므로 여기서 예외를 던져
                // 결제 응답을 실패로 되돌리지 않는다. 대신 결제와 신청서 상태가 어긋난
                // 상황이니 자동으로 환불을 시도해서 복구한다.
                log.error("참가비 결제 완료 통지 실패 — 신청서 상태 불일치. paymentId={}, applicationId={}",
                        row.getPaymentId(), row.getApplicationId(), e);
                try {
                    refundService.refund(row.getPaymentId(), SYSTEM_ACTOR_USER_ID,
                            new RefundRequest(RefundReason.VENDOR_CANCEL, RequestedByDomain.PAYMENT_ADMIN));
                } catch (Exception refundEx) {
                    // 환불까지 실패하면(정산 CONFIRMED 포함 등) 더는 자동으로 복구할 방법이
                    // 없어 로그만 남긴다 — 운영자가 결제·신청 상태를 보고 수동으로 맞춰야 한다.
                    log.error("자동 환불도 실패 — 수동 확인 필요. paymentId={}", row.getPaymentId(), refundEx);
                }
            }
        }

        recordPaymentCompletionAudit(row, userId);
        notifyPaymentCompleted(row);

        return PaymentResponse.from(row);
    }

    private void recordPaymentCompletionAudit(PaymentRow row, Long userId) {
        try {
            TargetType targetType = "RESERVATION_DEPOSIT".equals(row.getPaymentType()) && row.getReservationId() != null
                    ? TargetType.RESERVATION : TargetType.FAIR;
            Long targetId = targetType == TargetType.RESERVATION ? row.getReservationId() : row.getFairId();

            Map<String, Object> after = new LinkedHashMap<>();
            after.put("paymentId", row.getPaymentId());
            after.put("paymentType", row.getPaymentType());
            after.put("amount", row.getAmount());

            auditLogService.record(
                    userId,
                    ActorType.PAYMENT,
                    "USER",
                    ActionType.PAYMENT_COMPLETION_RECEIVED,
                    targetType,
                    targetId,
                    null,
                    after
            );
        } catch (Exception e) {
            log.error("결제 완료 감사 로그 저장 실패. paymentId={}", row.getPaymentId(), e);
        }
    }

    private void notifyPaymentCompleted(PaymentRow row) {
        try {
            notificationService.save(new SaveNotificationDto.Request(
                    row.getPayerUserId(),
                    RecipientType.USER,
                    NotificationType.PAYMENT_COMPLETED,
                    "결제가 완료되었습니다",
                    row.getAmount() + "원 결제가 정상적으로 처리되었습니다.",
                    null,
                    List.of(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
                    null
            ));
        } catch (Exception e) {
            log.error("결제 완료 알림 저장 실패. paymentId={}, userId={}",
                    row.getPaymentId(), row.getPayerUserId(), e);
        }
    }

    /**
     * 취소/만료 계약 API를 부를 수 있는 호출 도메인이 각각 어떤 결제유형(들)을 다룰 수 있는지
     * 매핑. {@code PaymentInternalAuthHeaders}의 캐스터 값과 대응 — 예약 도메인이 참가비 결제를,
     * 참가업체 도메인이 예약금 결제를 잘못(또는 악의적으로) 건드리는 걸 막는다.
     *
     * <p>FAIR는 예외적으로 세 유형을 전부 다룰 수 있다 - 행사가 취소되면 그 행사에 딸린
     * 예약금/참가비 PENDING 결제까지 Fair 도메인이 한 번에 정리한다. 이미 완료된 결제를
     * 환불하는 {@link com.ms.petopia.api.fair.service.FairCancelRefundOrchestrationService}와
     * 같은 방향(Fair 도메인이 취소된 행사의 결제 뒷정리를 전담) - reservation/vendor
     * application 도메인이 각자 fairs.canceled_at을 감지해서 반응하는 로직을 따로 만들지
     * 않아도 되게 하려는 목적이다.
     *
     * <p>인증 도메인 완성 전까지는 여전히 헤더값을 그대로 신뢰하는 한계는 남아있다(TODO).
     */
    private static final Map<String, Set<String>> CALLER_PAYMENT_TYPES = Map.of(
            "RESERVATION", Set.of("RESERVATION_DEPOSIT"),
            "FAIR", Set.of("FAIR_OPENING_FEE", "RESERVATION_DEPOSIT", "VENDOR_FEE"),
            "VENDOR_APPLICATION", Set.of("VENDOR_FEE")
    );

    /**
     * 다른 도메인이 자기 업무(예약/신청/행사)를 취소 처리하면서, 그에 딸린 PENDING 결제를
     * 함께 취소시키는 용도(WBS 1.7). PROCESSING(토스 승인 진행중)인 결제는 건드리면 안 되므로
     * PENDING에서만 허용한다 — CANCELED/EXPIRED는 영구 종료 상태라 재결제는 새 결제 생성으로
     * 처리한다(FAILED처럼 재사용하지 않음).
     *
     * @param callerDomain 호출 도메인(RESERVATION/FAIR/VENDOR_APPLICATION) — 그 결제의
     *                     paymentType이 이 도메인이 다룰 수 있는 유형에 없으면 남의 결제를
     *                     건드리는 셈이라 거부한다(FAIR는 예외적으로 세 유형 다 허용 -
     *                     {@link #CALLER_PAYMENT_TYPES} 참고).
     * @throws CommonException {@link ErrorCode#PAYMENT_NOT_FOUND} 존재하지 않는 결제 ID일 때
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 호출 도메인이 그 결제의 소유
     *         도메인이 아닐 때
     * @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} PENDING이 아니거나,
     *         조회 이후 다른 요청(confirm 등)이 먼저 상태를 바꿔버렸을 때
     */
    public PaymentResponse cancelPayment(Long paymentId, String callerDomain) {
        return changeToTerminalStatus(paymentId, callerDomain, "CANCELED", paymentMapper::markCanceled);
    }

    /**
     * 다른 도메인의 자체 만료 배치(결제기한 초과)가 호출해서 PENDING 결제를 만료 처리하는
     * 용도(WBS 1.7). 취소와 상태 가드·트레이드오프는 동일 — {@link #cancelPayment} 참고.
     */
    public PaymentResponse expirePayment(Long paymentId, String callerDomain) {
        return changeToTerminalStatus(paymentId, callerDomain, "EXPIRED", paymentMapper::markExpired);
    }

    private PaymentResponse changeToTerminalStatus(
            Long paymentId, String callerDomain, String targetStatus, BiFunction<Long, LocalDateTime, Integer> marker
    ) {
        PaymentRow row = paymentMapper.selectById(paymentId);
        if (row == null) {
            throw new CommonException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        // callerDomain이 CALLER_PAYMENT_TYPES에 등록 안 된 값(오타·미등록 호출자)이면
        // allowedTypes가 null이 되는데, 이걸 "제한 없음"으로 잘못 취급하면 등록 안 된 호출자가
        // 모든 결제유형을 건드릴 수 있게 열려버린다 - null도 명시적으로 거부한다.
        Set<String> allowedTypes = CALLER_PAYMENT_TYPES.get(callerDomain);
        if (allowedTypes == null || !allowedTypes.contains(row.getPaymentType())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
        if (!"PENDING".equals(row.getStatus())) {
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = marker.apply(paymentId, now);
        if (updated == 0) {
            // 조회 이후 confirm 등 다른 요청이 먼저 상태를 바꿔버린 경쟁 상황 — 충돌로 처리.
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        row.setStatus(targetStatus);
        row.setUpdatedAt(now);
        return PaymentResponse.from(row);
    }

    // eventId("PAYMENT_"+paymentId)가 매 시도마다 동일해서, 예약 도메인의 멱등 처리
    // 덕분에 재시도해도 안전하다(직전 시도가 실제로는 처리됐는데 응답만 유실된 경우도 커버).
    private static final int NOTIFY_MAX_ATTEMPTS = 3;
    private static final long NOTIFY_RETRY_DELAY_MS = 200;

    private void notifyReservationDomain(PaymentRow row) {
        for (int attempt = 1; attempt <= NOTIFY_MAX_ATTEMPTS; attempt++) {
            try {
                reservationPaymentContractClient.completePayment(
                        "PAYMENT_" + row.getPaymentId(),
                        row.getPaymentId(),
                        row.getReservationId(),
                        row.getAmount(),
                        row.getPaidAt()
                );
                return;
            } catch (Exception e) {
                if (attempt == NOTIFY_MAX_ATTEMPTS) {
                    // 결제 자체는 이미 성공했으므로 여기서 예외를 던져 결제 응답을 실패로 되돌리지 않는다.
                    // 재시도까지 다 실패하면 로그를 남겨서 운영 중 예약 확정 누락을 나중에 보정할 수 있게 한다.
                    // (배치/스케줄러로 자동 보정하는 건 별도 과제로 남겨둠 — 알려진 한계)
                    log.error("예약 도메인 결제완료 통지 {}회 재시도 모두 실패. reservationId={}, paymentId={}",
                            NOTIFY_MAX_ATTEMPTS, row.getReservationId(), row.getPaymentId(), e);
                    return;
                }
                log.warn("예약 도메인 결제완료 통지 실패({}번째 시도), 재시도한다. reservationId={}, paymentId={}",
                        attempt, row.getReservationId(), row.getPaymentId(), e);
                // 대기 중 인터럽트(취소 신호) 걸리면 재시도를 더 돌리지 않고 바로 빠져나간다.
                if (!sleepBeforeRetry()) {
                    return;
                }
            }
        }
    }

    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(NOTIFY_RETRY_DELAY_MS);
            return true;
        } catch (InterruptedException interruptedException) {
            // 인터럽트 상태를 삼키지 않고 다시 세팅 — 스레드 종료/취소 신호를 존중하기 위함.
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
