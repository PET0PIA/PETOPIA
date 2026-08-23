package com.ms.petopia.api.payment.service;

import com.ms.petopia.api.application.service.ApplicationService;
import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.auth.service.MailService;
import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.payment.client.ApplicationPaymentContractClient;
import com.ms.petopia.api.payment.client.FairOpeningFeePaymentContractClient;
import com.ms.petopia.api.payment.client.ReservationPaymentContractClient;
import com.ms.petopia.api.payment.client.TossPaymentClient;
import com.ms.petopia.api.payment.dto.*;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.api.refund.dto.RefundReason;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RequestedByDomain;
import com.ms.petopia.api.refund.service.RefundService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.UUID;
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
    private final FairOpeningFeePaymentContractClient fairOpeningFeePaymentContractClient;
    private final ApplicationPaymentContractClient applicationPaymentContractClient;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ApplicationService applicationService;
    private final RefundService refundService;
    private final FairAdminAccessGuard fairAdminAccessGuard;
    private final RecruitNoticeMapper recruitNoticeMapper;
    private final AuthMapper authMapper;
    private final MailService mailService;

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
     * 결제 ID로 상세 조회한다(HTTP 진입점 전용). 위 {@link #getPayment(Long)}과 달리
     * 소유자(payerUserId) 또는 그 행사 담당 EVENT_ADMIN/SUPER_ADMIN인지 검증한다 —
     * {@link #getPayment(Long)}은 SecurityContext 없는 내부 도메인간 직접호출
     * (예: {@code FairCancelRefundOrchestrationService})에서도 쓰이므로 그쪽엔 가드를 넣지 않는다.
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_NOT_FOUND} 존재하지 않는 결제 ID일 때
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 소유자도, 그 행사 담당 관리자도 아닐 때
     */
    public PaymentResponse getPayment(Long paymentId, Long userId) {
        PaymentRow row = paymentMapper.selectByIdWithRefund(paymentId);
        if (row == null) {
            throw new CommonException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (!userId.equals(row.getPayerUserId())) {
            fairAdminAccessGuard.checkAssigned(row.getFairId());
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
     * 참가비 결제를 생성한다. 참가업체 도메인의 결제 컨텍스트 조회로 승인 시 확정된 진짜 금액을
     * 받아온다 — 예약금/개설비와 동일하게, 클라이언트가 보낸 금액은 더 이상 신뢰하지 않는다
     * (2026-08-20 해소. 채린님이 {@code ApplicationPaymentContractController}를 먼저 열어주셔서
     * 이제 결제 3종이 전부 같은 패턴으로 통일됨 — 원래는 application 테이블을 조회하지 않는다는
     * 원칙 때문에 클라이언트 입력을 그대로 썼던 트레이드오프였다).
     *
     * <p>동일 참가신청에 대한 중복 결제는 idempotencyKey(UK_PAYMENT_IDEMPOTENCY_KEY)로
     * DB가 막는다 — 여기서 잡아 {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE}로 변환한다.
     *
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 사업자 소유주가 아닐 때
     * @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} 신청이 결제 가능한
     *         상태가 아니거나 이미 결제된 참가신청이거나, 컨텍스트 응답의 applicationId가
     *         요청한 값과 다를 때(참가업체 도메인 쪽 버그 방어, CodeRabbit 리뷰 지적)
     */
    @Transactional
    public PaymentResponse payVendorFee(Long applicationId, Long userId) {
        ApplicationVendorFeePaymentContext context = applicationPaymentContractClient.getPaymentContext(applicationId);
        // 정상적이라면 항상 같아야 한다(같은 ID로 조회를 요청했으니까) — 그래도 상대 도메인의
        // 응답 로직에 버그가 있거나 나중에 바뀌었을 때 엉뚱한 신청의 금액으로 결제가 만들어지는
        // 사고를 막기 위한 방어선이다.
        if (!applicationId.equals(context.applicationId())) {
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }
        if (!userId.equals(context.payerUserId())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }

        String idempotencyKey = "VENDOR_FEE_" + applicationId;
        PaymentRow row = createOrRetryPayment(idempotencyKey, context.amount(), userId, () -> {
            LocalDateTime now = LocalDateTime.now();
            PaymentRow newRow = new PaymentRow();
            newRow.setPaymentType("VENDOR_FEE");
            newRow.setAmount(context.amount());
            newRow.setStatus("PENDING");
            newRow.setMethod("TOSS");
            newRow.setIdempotencyKey(idempotencyKey);
            newRow.setCreatedAt(now);
            newRow.setUpdatedAt(now);
            newRow.setFairId(context.fairId());
            newRow.setBusinessId(context.businessId());
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
     * 행사개설비 결제를 생성한다. 예약금과 동일하게 클라이언트가 금액을 보내지 않는다 - 행사
     * 도메인의 내부 계약({@link FairOpeningFeePaymentContractClient})을 호출해 승인 시 확정된
     * 금액을 받아온다. fairId만 채워지고 businessId·reservationId·applicationId는 전부 null.
     *
     * <p>개설비는 승인받은 당사자(그 행사 담당 EVENT_ADMIN) 또는 SUPER_ADMIN이 결제할 수 있다
     * ({@link FairAdminAccessGuard#checkAssigned}, 2026-08-21 정정 — 원래 "SUPER_ADMIN만 대행
     * 결제"로 좁혀뒀었는데, 다시 보니 승인(SUPER_ADMIN의 심사 액션)과 결제(승인받은 당사자가
     * 내는 돈)를 혼동한 설계였다. 예약금·참가비처럼 그 일의 당사자가 직접 내는 흐름으로
     * 맞추고, SUPER_ADMIN도 관리 목적상 계속 결제할 수 있게 뒀다).
     *
     * <p>결제 완료 후 행사 상태를 "준비중"으로 전이하는 건 이 메서드 책임이 아니다 - 행사
     * 도메인이 폴링 방식으로 직접 감지해서 전이한다({@link
     * com.ms.petopia.api.fair.service.FairTransitionService#completeDuePayments}, 5분 간격,
     * 2026-08-07 결정 - confirmPayment()가 예약금과 달리 개설비는 크로스도메인 콜백을 보내지
     * 않는 걸 확인하고 폴링으로 확정). 지금은 결제 자체만 처리하고 크로스도메인 통지는 하지
     * 않는다(참가비와 동일).
     *
     * <p>동일 행사에 대한 중복 결제는 idempotencyKey(UK_PAYMENT_IDEMPOTENCY_KEY)로
     * DB가 막는다 — 여기서 잡아 {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE}로 변환한다.
     *
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 그 행사 담당 EVENT_ADMIN도
     *         SUPER_ADMIN도 아닐 때
     * @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} 이미 결제된 행사이거나,
     *         존재하지 않거나 개설비를 결제할 수 없는 상태의 행사일 때
     */
    @Transactional
    public PaymentResponse payFairOpeningFee(Long fairId, Long userId) {
        fairAdminAccessGuard.checkAssigned(fairId);
        FairOpeningFeePaymentContext context = fairOpeningFeePaymentContractClient.getPaymentContext(fairId);

        String idempotencyKey = "FAIR_OPENING_FEE_" + fairId;
        PaymentRow row = createOrRetryPayment(idempotencyKey, context.amount(), userId, () -> {
            LocalDateTime now = LocalDateTime.now();
            PaymentRow newRow = new PaymentRow();
            newRow.setPaymentType("FAIR_OPENING_FEE");
            newRow.setAmount(context.amount());
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
     *   <li>PENDING(결제창을 띄웠다 닫은 진행 중 결제)이고 요청자가 원래 결제자면, 그 행을 '그대로'
     *       (같은 orderId·금액) 돌려줘 결제창을 다시 열 수 있게 함. 결과 미상이라 새 orderId를 발급하면
     *       이미 승인된 건을 재결제할 위험이 있어, 일부러 orderId를 바꾸지 않는다(RETRYABLE_TERMINAL_STATUSES
     *       재시도와 다른 점)</li>
     *   <li>{@link #RETRYABLE_TERMINAL_STATUSES}(FAILED/CANCELED/EXPIRED)로 남아있고 요청자가 그
     *       결제의 원래 결제자면, 그 행을 PENDING으로 되돌려 재사용(재결제 허용, 새 orderId 발급).
     *       원래는 FAILED만 허용했는데(2026-08-06), CANCELED/EXPIRED는 영구종료로 남겨뒀던 걸
     *       2026-08-20에 같이 풀었다 — 참가비/개설비처럼 원업무 ID(applicationId/fairId)가 고정된
     *       유형은, 취소·만료된 신청을 나중에 다시 결제해야 하는 상황이 있으면 지금까지는 그
     *       idempotencyKey가 영영 막혀서 새 결제 자체를 못 만들었다</li>
     *   <li>PENDING이거나 RETRYABLE_TERMINAL_STATUSES인데 요청자가 원래 결제자가 아니면
     *       ACCESS_DENIED(남의 결제 재개/재시도 금지)</li>
     *   <li>PROCESSING/COMPLETED면 여전히 중복결제로 막음</li>
     * </ul>
     *
     * @param idempotencyKey 원업무 식별자 기준 키(예: {@code "VENDOR_FEE_" + applicationId})
     * @param amount 이번 시도의 결제 금액 — 재사용 시에도 이 값으로 갱신한다(재시도 시점에
     *               금액이 달라질 수 있어서, 예: 참가비 재승인 등)
     * @param userId 재시도를 요청한 사용자. 기존 재시도가능 행의 payerUserId와 다르면 남의 결제를
     *               멋대로 PENDING으로 되돌리는 셈이라 막는다(CodeRabbit 지적, PR #63).
     * @param newRowSupplier 이전 시도가 아예 없을 때 삽입할 새 PaymentRow를 만드는 함수
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 기존 재시도가능 결제의 결제자가 아닐 때
     * @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} 이미 결제 진행/완료 중이거나,
     *         다른 요청이 먼저 재시도를 선점했을 때
     */
    /**
     * 토스 승인용 주문번호를 발급한다.
     *
     * <p>결제 행이 아니라 <b>승인 시도</b> 단위로 유일해야 한다. 결제 행은 예약 1건당
     * 하나로 고정이라(UK_PAYMENT_IDEMPOTENCY_KEY) payment_id로 만들면 재시도할 때마다
     * 같은 값이 나오고, 그 주문번호에 이미 승인 건이 잡혀 있으면 영영 결제할 수 없게 된다.
     *
     * <p>앞의 {@code PAYMENT_}는 토스 대시보드에서 우리 결제 건임을 알아보기 위한 것이고,
     * 유일성은 뒤의 UUID가 책임진다. 하이픈을 빼 32자로 만들어 전체 40자 — 토스 주문번호
     * 제약(6~64자, 영문·숫자·하이픈·언더스코어) 안에 들어간다.
     */
    private String newOrderId() {
        return "PAYMENT_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * createOrRetryPayment이 PENDING으로 되살려 재시도를 허용하는 종료 상태.
     * PROCESSING(승인 진행 중)·COMPLETED(이미 결제됨)는 여기 없다 — 돈이 이미 움직였거나
     * 움직이는 중이라 절대 되살리면 안 된다.
     */
    private static final Set<String> RETRYABLE_TERMINAL_STATUSES = Set.of("FAILED", "CANCELED", "EXPIRED");

    private PaymentRow createOrRetryPayment(
            String idempotencyKey, Long amount, Long userId, Supplier<PaymentRow> newRowSupplier
    ) {
        PaymentRow existing = paymentMapper.selectByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            // PENDING = 결제창을 띄웠다가 닫은, 결과를 아직 모르는 진행 중 결제. 같은 사용자면 그 행을
            // '그대로'(같은 orderId·금액) 돌려줘 결제창을 다시 열 수 있게 한다(결제 제한시간 내 재개).
            //
            // ★ FAILED와 달리 새 orderId를 발급하지 않는다. FAILED는 토스가 '실패'를 확정해준 상태라
            //   새 주문번호로 재시도해도 안전하지만, PENDING은 결과 미상이다. 결제창에서 이미 승인됐는데
            //   확정 통보만 못 받아 PENDING으로 남은 것이라면, 새 orderId로 다시 열면 이중결제가 난다.
            //   같은 orderId를 그대로 쓰면 (1) 미결제였으면 결제창이 다시 열리고, (2) 몰래 승인됐었으면
            //   토스가 "이미 처리된 주문번호"로 거부해 이중결제를 막는다(그 결제는 완료 통지로 정산).
            if ("PENDING".equals(existing.getStatus())) {
                if (!userId.equals(existing.getPayerUserId())) {
                    throw new CommonException(ErrorCode.ACCESS_DENIED);
                }
                return existing;
            }
            if (!RETRYABLE_TERMINAL_STATUSES.contains(existing.getStatus())) {
                // PROCESSING(승인 진행 중)·COMPLETED(이미 결제됨) 등은 계속 중복결제로 막는다.
                throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
            }
            if (!userId.equals(existing.getPayerUserId())) {
                throw new CommonException(ErrorCode.ACCESS_DENIED);
            }

            LocalDateTime now = LocalDateTime.now();
            // 재시도는 새 주문번호로 나간다. 이전 시도의 주문번호에는 토스 쪽에 이미
            // 승인 건이 잡혀 있을 수 있어(CANCELED/EXPIRED는 가상계좌 발급까지 됐던 것일 수
            // 있음), 재사용하면 "이미 처리된 주문번호"로 거부당한다.
            String retryOrderId = newOrderId();
            int reset = paymentMapper.resetRetryableToPending(
                    existing.getPaymentId(), amount, retryOrderId, now);
            if (reset == 0) {
                // 우리가 조회한 뒤, 다른 요청이 먼저 재시도를 선점했거나 상태가 바뀐 경우 — 충돌로 처리.
                throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
            }

            existing.setStatus("PENDING");
            existing.setAmount(amount);
            existing.setOrderId(retryOrderId);
            existing.setUpdatedAt(now);
            existing.setMethod("TOSS");
            existing.setTossPaymentKey(null);
            existing.setPaidAt(null);
            existing.setEasyPayProvider(null);
            existing.setVirtualAccountBankCode(null);
            existing.setVirtualAccountNumber(null);
            existing.setVirtualAccountDueDate(null);
            existing.setVirtualAccountSecret(null);
            return existing;
        }

        PaymentRow row = newRowSupplier.get();
        row.setOrderId(newOrderId());
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
     * <p>다른 도메인(승훈님 {@code FairCancelRefundOrchestrationService}/{@code
     * FairCancelPendingPaymentService})이 빈 주입으로 직접 호출하는 기존 시그니처라, 남의
     * 도메인 호출부를 건드리지 않도록 이 오버로드는 그대로 두고 reservationId 필터는 아래
     * 새 오버로드로 분리했다(2026-08-21, 관리자 결제 목록 화면의 "ID유형" 통합검색용).
     *
     * @throws CommonException {@link ErrorCode#INVALID_INPUT_VALUE} page·size가 범위를 벗어났을 때
     */
    public PaymentListResponse getPayments(
            Long fairId, Long businessId, String paymentType, String status, int page, int size
    ) {
        return getPayments(fairId, businessId, paymentType, status, null, page, size);
    }

    /** {@link #getPayments(Long, Long, String, String, int, int)}에 reservationId 필터를 더한 버전. */
    public PaymentListResponse getPayments(
            Long fairId, Long businessId, String paymentType, String status, Long reservationId, int page, int size
    ) {
        validatePageAndSize(page, size);
        long offset = (long) page * size;
        List<PaymentRow> rows = paymentMapper.selectByFilter(fairId, businessId, paymentType, status, null, reservationId, offset, size);
        long total = paymentMapper.countByFilter(fairId, businessId, paymentType, status, null, reservationId);
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
        List<PaymentRow> rows = paymentMapper.selectByFilter(null, null, null, null, userId, null, offset, size);
        long total = paymentMapper.countByFilter(null, null, null, null, userId, null);
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

        // 저장된 주문번호를 그대로 쓴다. 여기서 다시 만들면 프론트가 결제창에 넘긴
        // 값과 어긋나 승인이 통째로 실패한다.
        String orderId = row.getOrderId();
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

        // 토스 confirm 응답 status를 반드시 확인한다 — "WAITING_FOR_DEPOSIT"(가상계좌 발급,
        // 입금 전)인데 확인 없이 무조건 COMPLETED로 확정하면 아직 입금도 안 된 결제가 완료로
        // 잘못 표시된다. 진짜 완료(status="DONE")만 이 자리에서 COMPLETED로 확정하고,
        // WAITING_FOR_DEPOSIT은 별도 상태로 남겨서 입금 웹훅(handleDepositWebhook)이 나중에
        // COMPLETED로 전환한다.
        if ("WAITING_FOR_DEPOSIT".equals(tossResponse.status())) {
            rejectVirtualAccountForReservationDeposit(row, tossResponse);
            return markWaitingForDeposit(row, tossResponse, now);
        }

        row.setStatus("COMPLETED");
        row.setMethod(tossResponse.method());
        row.setTossPaymentKey(tossResponse.paymentKey());
        row.setEasyPayProvider(tossResponse.easyPay() != null ? tossResponse.easyPay().provider() : null);
        row.setPaidAt(now);
        row.setUpdatedAt(now);

        int updated = paymentMapper.markCompleted(row);
        if (updated == 0) {
            // markProcessing으로 선점에 성공한 요청만 여기 도달하므로 이론상 발생하지
            // 않아야 하지만, 방어적으로 남겨둔다.
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        completePaymentAndNotify(row, userId);

        return PaymentResponse.from(row);
    }

    /**
     * 예약금(RESERVATION_DEPOSIT) 결제는 가상계좌를 지원하지 않는다 — 예약 결제 제한시간은 10분
     * ({@code ReservationPaymentPolicy.PAYMENT_WAIT})인데 가상계좌 입금기한은 하루 단위라, 뒤늦게
     * 입금되면 "결제는 COMPLETED인데 예약은 EXPIRED"가 되어 돈만 받은 상태가 된다(이슈 #167).
     * 참가비·개설비는 결제 기한이 일 단위여서 계속 가상계좌를 쓸 수 있으므로, 결제유형으로만 가른다.
     *
     * <p>1차 차단은 화면이다 — 예약금 결제 화면에는 가상계좌 선택지가 없고, 프론트도 타입으로
     * 막아둔다(frontend/src/payments/toss.ts의 {@code ReservationPaymentMethod}). 여기는 그 화면을
     * 우회한 요청을 위한 최종 방어선이다.
     *
     * <p><b>알려진 한계</b>: confirm 시점엔 토스가 이미 계좌를 발급한 뒤라, 우리가 거절해도 계좌
     * 자체는 은행에 남는다. 발급된 계좌를 실제로 닫으려면 토스 결제취소 API
     * ({@code POST /v1/payments/{paymentKey}/cancel}, 입금 전이면 cancelReason만 필요) 연동이
     * 있어야 하는데 이 프로젝트엔 아직 없다(환불도 PG 미연동). 그래서 여기서는
     * (1) 계좌정보·secret을 저장하지 않아 이후 입금 웹훅이 secret 대조에서 걸러지게 하고,
     * (2) 운영자가 토스 콘솔에서 그 계좌를 찾아 닫을 수 있도록 paymentKey까지 로그로 남긴다.
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_METHOD_NOT_ALLOWED} 예약금 결제인데
     *         가상계좌로 승인 요청이 들어왔을 때
     */
    private void rejectVirtualAccountForReservationDeposit(PaymentRow row, TossPaymentResponse tossResponse) {
        if (!"RESERVATION_DEPOSIT".equals(row.getPaymentType())) {
            return;
        }

        // 이 결제는 여기서 끝낸다(재시도 가능한 FAILED). 계좌정보는 저장하지 않는다.
        paymentMapper.markFailed(row.getPaymentId(), LocalDateTime.now());
        TossPaymentResponse.VirtualAccount virtualAccount = tossResponse.virtualAccount();
        log.error("예약금 결제에 가상계좌가 시도돼 승인을 거절함 - 토스 콘솔에서 발급된 계좌를 닫아야 한다. "
                        + "paymentId={}, reservationId={}, orderId={}, paymentKey={}, bankCode={}",
                row.getPaymentId(), row.getReservationId(), row.getOrderId(), tossResponse.paymentKey(),
                virtualAccount != null ? virtualAccount.bankCode() : null);
        throw new CommonException(ErrorCode.PAYMENT_METHOD_NOT_ALLOWED);
    }

    /**
     * 토스 confirm 응답이 가상계좌 발급(WAITING_FOR_DEPOSIT)일 때 그 상태로 저장한다.
     * 계좌정보와 웹훅검증용 secret을 같이 저장해야, 나중에 입금 완료 웹훅이 왔을 때 secret을
     * 대조해 위조를 막을 수 있다(handleDepositWebhook 참고).
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_GATEWAY_UNAVAILABLE} status는
     *         WAITING_FOR_DEPOSIT인데 virtualAccount 필드가 없는 이상한 응답일 때
     * @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} markProcessing으로
     *         선점한 뒤인데도 전이가 실패한 방어적 케이스(이론상 발생하지 않아야 함)
     */
    private PaymentResponse markWaitingForDeposit(PaymentRow row, TossPaymentResponse tossResponse, LocalDateTime now) {
        TossPaymentResponse.VirtualAccount virtualAccount = tossResponse.virtualAccount();
        if (virtualAccount == null) {
            log.error("토스 응답이 WAITING_FOR_DEPOSIT인데 virtualAccount가 없음. paymentId={}, orderId={}",
                    row.getPaymentId(), row.getOrderId());
            throw new CommonException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }

        row.setStatus("WAITING_FOR_DEPOSIT");
        row.setMethod(tossResponse.method());
        row.setTossPaymentKey(tossResponse.paymentKey());
        row.setVirtualAccountBankCode(virtualAccount.bankCode());
        row.setVirtualAccountNumber(virtualAccount.accountNumber());
        // dueDate는 토스가 오프셋 붙은 값으로 내려주므로(TossPaymentResponse.VirtualAccount
        // 참고) OffsetDateTime으로 받았다가 여기서 LocalDateTime으로 변환해 저장한다 - 서버·DB가
        // 전부 Asia/Seoul 고정이라 오프셋을 버려도 벽시계 시각은 그대로 맞다.
        row.setVirtualAccountDueDate(virtualAccount.dueDate() != null ? virtualAccount.dueDate().toLocalDateTime() : null);
        row.setVirtualAccountSecret(virtualAccount.secret());
        row.setUpdatedAt(now);

        int updated = paymentMapper.markWaitingForDeposit(row);
        if (updated == 0) {
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }
        return PaymentResponse.from(row);
    }

    /**
     * 가상계좌 입금 웹훅(POST /webhooks/toss/deposit-callback)을 처리한다. 웹훅은 JWT 없이
     * 토스가 직접 호출하므로(SecurityConfig에 permitAll), secret 대조로 위조를 막는다 — 토스
     * 공식 문서 기준 가상계좌 웹훅은 HMAC 서명이 아니라 발급 응답의 secret과 웹훅 body의
     * secret 비교가 공식 검증방식이다.
     *
     * <p>대상 없음/이미 처리됨/secret 불일치는 전부 예외를 던지지 않고 로그만 남기고 조용히
     * 끝낸다 — 컨트롤러는 이 메서드가 끝나면 항상 200을 반환해서, 토스가 실패로 보고
     * 불필요하게 재전송을 반복하지 않게 한다.
     */
    public void handleDepositWebhook(TossWebhookEvent event) {
        if (event == null || event.data() == null || !"DEPOSIT_CALLBACK".equals(event.eventType())) {
            return;
        }
        TossWebhookEvent.Data data = event.data();
        PaymentRow row = paymentMapper.selectByOrderId(data.orderId());
        if (row == null) {
            log.warn("입금 웹훅 대상 결제를 찾을 수 없음. orderId={}", data.orderId());
            return;
        }
        if (!"WAITING_FOR_DEPOSIT".equals(row.getStatus())) {
            // 이미 처리됐거나(웹훅 재전송) 애초에 가상계좌 결제가 아닌 경우 — 멱등하게 무시한다.
            //
            // 다만 "예약금인데 입금완료가 들어왔고 결제는 COMPLETED가 아닌" 조합은 조용히 넘기면
            // 안 된다. 예약금은 가상계좌를 지원하지 않으므로(rejectVirtualAccountForReservationDeposit)
            // 승인 단계에서 거절해 FAILED로 남긴 계좌에 뒤늦게 입금이 들어온 상황이고, 우리 원장에는
            // 안 남는 돈이라 자동 복구가 불가능하다. 여기는 secret 대조 전 단계여서 위조 웹훅으로도
            // 찍힐 수 있으니, 로그를 보고 토스 콘솔에서 실제 입금을 확인한 뒤 수동 환불해야 한다.
            if ("RESERVATION_DEPOSIT".equals(row.getPaymentType())
                    && "DONE".equals(data.status())
                    && !"COMPLETED".equals(row.getStatus())) {
                log.error("지원하지 않는 예약금 가상계좌에 입금완료 웹훅이 도착함(secret 미검증 단계) - "
                                + "토스 콘솔에서 실제 입금 확인 후 수동 환불 필요. paymentId={}, reservationId={}, "
                                + "orderId={}, paymentStatus={}",
                        row.getPaymentId(), row.getReservationId(), data.orderId(), row.getStatus());
            }
            return;
        }
        // Objects.equals(null, null)이 true라서, 저장된 secret이 비어있는 결제(버그로 저장이
        // 빠졌거나 하는 경우)에 웹훅 body도 secret 없이 오면 위조 검증 자체가 통과해버리는
        // 문제가 있었다(CodeRabbit 리뷰 지적, 실제 위조방지 우회 가능한 보안 버그) — 양쪽 다
        // 값이 있어야만 비교를 통과하도록 막는다.
        String storedSecret = row.getVirtualAccountSecret();
        if (!StringUtils.hasText(storedSecret) || !StringUtils.hasText(data.secret())
                || !storedSecret.equals(data.secret())) {
            log.warn("입금 웹훅 secret 불일치 — 위조 의심. paymentId={}, orderId={}",
                    row.getPaymentId(), data.orderId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if ("DONE".equals(data.status())) {
            int updated = paymentMapper.markVirtualAccountCompleted(row.getPaymentId(), now, now);
            if (updated == 0) {
                return; // 동시에 다른 요청(웹훅 재전송)이 먼저 처리함
            }
            row.setStatus("COMPLETED");
            row.setPaidAt(now);
            row.setUpdatedAt(now);
            // 가상계좌는 사람(관리자)이 아니라 결제자 본인의 계좌이체가 완료 트리거이므로,
            // 감사로그 행위자는 confirmPayment의 로그인 사용자 대신 결제자 본인으로 남긴다.
            completePaymentAndNotify(row, row.getPayerUserId());
        } else if ("CANCELED".equals(data.status())) {
            int failed = paymentMapper.markVirtualAccountDepositFailed(row.getPaymentId(), now);
            if (failed == 0) {
                log.warn("가상계좌 입금취소 웹훅 상태전이 실패 - 이미 다른 요청이 처리함. paymentId={}",
                        row.getPaymentId());
            }
        } else {
            log.warn("알 수 없는 입금 웹훅 status. paymentId={}, status={}", row.getPaymentId(), data.status());
        }
    }

    /**
     * 결제 완료 후속처리(도메인 통지·감사로그·완료알림)를 {@link #confirmPayment}(카드/일반결제
     * 즉시완료)와 {@link #handleDepositWebhook}(가상계좌 입금완료) 둘이 공유한다. 호출 전에
     * row.status는 이미 COMPLETED로 확정돼 있어야 한다.
     *
     * @param actingUserId 감사로그에 남길 행위자
     */
    private void completePaymentAndNotify(PaymentRow row, Long actingUserId) {
        if ("RESERVATION_DEPOSIT".equals(row.getPaymentType())) {
            notifyReservationDomain(row);
        }

        // 참가비 결제 완료를 참가업체 도메인에 통지해 신청 상태를 PAYMENT_PENDING -> CONFIRMED로
        // 전환시키고 부스를 자동 생성시킨다.
        if ("VENDOR_FEE".equals(row.getPaymentType())) {
            try {
                applicationService.confirmVendorPayment(row.getApplicationId(), row.getPaymentId(), row.getAmount());
            } catch (CommonException | DataAccessException e) {
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

        recordPaymentCompletionAudit(row, actingUserId);
        notifyPaymentCompleted(row);
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
        // RESERVATION_DEPOSIT은 예약 도메인이 별도로 RESERVATION_CONFIRMED를 보내므로
        // (ReservationPaymentCompletionService.complete() 참고) 사용자에게는 이 알림을 생략한다 —
        // 안 그러면 같은 결제 1건에 "결제 완료"와 "예약 확정" 알림이 중복으로 간다.
        if (!"RESERVATION_DEPOSIT".equals(row.getPaymentType())) {
            try {
                notificationService.save(new SaveNotificationDto.Request(
                        row.getPayerUserId(),
                        RecipientType.USER,
                        NotificationType.PAYMENT_COMPLETED,
                        "결제가 완료되었습니다",
                        row.getAmount() + "원 결제가 정상적으로 처리되었습니다.",
                        paymentDetailLinkUrl(row),
                        List.of(DeliveryChannel.IN_APP),
                        null
                ));
            } catch (Exception e) {
                log.error("결제 완료 알림 저장 실패. paymentId={}, userId={}",
                        row.getPaymentId(), row.getPayerUserId(), e);
            }
            sendPaymentCompletedEmail(row);
        }
        notifyPaymentCompletedToAdmins(row);
    }

    /** 결제 유형별로 결제자가 확인해야 할 상세 화면을 가리킨다. 알 수 없는 유형이면 링크 없이 둔다. */
    private String paymentDetailLinkUrl(PaymentRow row) {
        if ("VENDOR_FEE".equals(row.getPaymentType()) && row.getApplicationId() != null) {
            return "/participations/me/" + row.getApplicationId();
        }
        if ("FAIR_OPENING_FEE".equals(row.getPaymentType()) && row.getFairId() != null) {
            return "/fair-applications/me/" + row.getFairId();
        }
        return null;
    }

    private void sendPaymentCompletedEmail(PaymentRow row) {
        try {
            User user = authMapper.selectUserById(row.getPayerUserId());
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                return;
            }
            String fairName = row.getFairId() == null ? null : paymentMapper.selectFairNameById(row.getFairId());
            mailService.sendPaymentCompletedEmail(user.getEmail(), fairName, row.getAmount(), row.getMethod(), row.getPaidAt());
        } catch (Exception e) {
            log.error("결제 완료 이메일 발송 실패. paymentId={}, userId={}",
                    row.getPaymentId(), row.getPayerUserId(), e);
        }
    }

    /** 결제 발생을 행사 담당 EVENT_ADMIN에게 알린다. 실패해도 결제 처리에는 영향 없음. */
    private void notifyPaymentCompletedToAdmins(PaymentRow row) {
        String payerNickname = resolvePayerNickname(row.getPayerUserId());
        String body = payerNickname + "님이 " + row.getAmount() + "원을 결제했습니다.";

        try {
            Long adminUserId = row.getFairId() == null
                    ? null : recruitNoticeMapper.selectAdminUserIdByFairId(row.getFairId());
            if (adminUserId != null) {
                notificationService.save(new SaveNotificationDto.Request(
                        adminUserId,
                        RecipientType.EVENT_ADMIN,
                        NotificationType.PAYMENT_COMPLETED,
                        "결제가 접수되었습니다",
                        body,
                        adminPaymentLinkUrl(row),
                        List.of(DeliveryChannel.IN_APP),
                        null
                ));
            }
        } catch (Exception e) {
            log.error("결제 완료 EVENT_ADMIN 알림 저장 실패. paymentId={}, fairId={}",
                    row.getPaymentId(), row.getFairId(), e);
        }
    }

    /** 예약금 결제는 예약현황 화면으로, 그 외(참가비·개설비)는 결제현황 화면으로 관리자를 안내한다. */
    private String adminPaymentLinkUrl(PaymentRow row) {
        if ("RESERVATION_DEPOSIT".equals(row.getPaymentType())) {
            return "/fair-admin/reservations?fairId=" + row.getFairId();
        }
        return "/fair-admin/payments?fairId=" + row.getFairId();
    }

    private String resolvePayerNickname(Long payerUserId) {
        try {
            User user = authMapper.selectUserById(payerUserId);
            return user != null && user.getNickname() != null ? user.getNickname() : "알 수 없는 사용자";
        } catch (Exception e) {
            log.warn("결제자 닉네임 조회 실패. payerUserId={}", payerUserId, e);
            return "알 수 없는 사용자";
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
     * cancelPayment/expirePayment가 건드릴 수 있는 상태 — PENDING(결제 시작 전)뿐 아니라
     * WAITING_FOR_DEPOSIT(가상계좌 발급, 아직 입금 전)도 포함한다. 가상계좌는 confirm이
     * 성공해도 실제 돈은 아직 안 움직인 상태라 PENDING과 마찬가지로 로컬에서 안전하게
     * 종료시킬 수 있다(PROCESSING·COMPLETED와 다른 점 — 이 둘은 카드 승인이 진행 중이거나
     * 이미 끝나 돈이 움직였으므로 여전히 건드리지 않는다).
     *
     * <p>WAITING_FOR_DEPOSIT을 CANCELED/EXPIRED로 바꾸기 직전에 {@link #changeToTerminalStatus}가
     * 토스 결제취소 API({@link com.ms.petopia.api.payment.client.TossPaymentClient#cancelVirtualAccount})를
     * 먼저 호출해 실제 은행 가상계좌도 함께 닫는다(2026-08-20 해소 — 예전엔 로컬 상태만 바뀌고
     * 실제 계좌는 열려있어 뒤늦은 입금을 아무도 못 잡아내는 known limitation이었음). 토스 호출이
     * 실패하면 로컬 상태도 바꾸지 않고 예외를 던진다.
     */
    private static final Set<String> CANCELABLE_STATUSES = Set.of("PENDING", "WAITING_FOR_DEPOSIT");

    /**
     * 다른 도메인이 자기 업무(예약/신청/행사)를 취소 처리하면서, 그에 딸린 결제를 함께
     * 취소시키는 용도(WBS 1.7). {@link #CANCELABLE_STATUSES}에서만 허용한다 —
     * PROCESSING(토스 승인 진행중)·COMPLETED는 이미 돈이 움직였을 수 있어 건드리지 않는다.
     * CANCELED/EXPIRED로 바뀐 뒤에도 원업무가 재결제를 요청하면 {@link #createOrRetryPayment}가
     * {@link #RETRYABLE_TERMINAL_STATUSES}를 통해 FAILED와 동일하게 PENDING으로 되살려 재사용한다
     * (2026-08-20 해소 — 참가비/개설비처럼 원업무 ID가 고정된 유형은 그전까지 영구히 재결제
     * 불가능했음).
     *
     * @param callerDomain 호출 도메인(RESERVATION/FAIR/VENDOR_APPLICATION) — 그 결제의
     *                     paymentType이 이 도메인이 다룰 수 있는 유형에 없으면 남의 결제를
     *                     건드리는 셈이라 거부한다(FAIR는 예외적으로 세 유형 다 허용 -
     *                     {@link #CALLER_PAYMENT_TYPES} 참고).
     * @throws CommonException {@link ErrorCode#PAYMENT_NOT_FOUND} 존재하지 않는 결제 ID일 때
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 호출 도메인이 그 결제의 소유
     *         도메인이 아닐 때
     * @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} 취소 가능한 상태가
     *         아니거나, 조회 이후 다른 요청(confirm 등)이 먼저 상태를 바꿔버렸을 때
     */
    public PaymentResponse cancelPayment(Long paymentId, String callerDomain) {
        return changeToTerminalStatus(paymentId, callerDomain, "CANCELED", paymentMapper::markCanceled);
    }

    /**
     * 다른 도메인의 자체 만료 배치(결제기한 초과)가 호출해서 결제를 만료 처리하는
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
        if (!CANCELABLE_STATUSES.contains(row.getStatus())) {
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        // WAITING_FOR_DEPOSIT(가상계좌 발급, 입금 전)이면 로컬 상태를 바꾸기 전에 먼저 토스
        // 쪽 계좌를 실제로 닫는다. 순서가 중요하다 — 토스 호출이 실패했는데도 로컬을 먼저
        // CANCELED/EXPIRED로 바꿔버리면, 실제로는 아직 열려있는 계좌로 뒤늦게 입금이 들어와도
        // handleDepositWebhook의 WHERE status='WAITING_FOR_DEPOSIT' 가드에 안 걸려 조용히
        // 무시된다(예전에 알려진 한계로 남겨뒀던 문제, 이제 여기서 막는다). 토스 실패는
        // CommonException을 그대로 던져 로컬 상태도 안 바꾼다 — 호출한 배치(FairCancelPendingPaymentService
        // 등)는 이미 각 건을 try-catch로 개별 처리하므로, 이 한 건만 다음 배치 주기에 재시도된다.
        if ("WAITING_FOR_DEPOSIT".equals(row.getStatus())) {
            String tossPaymentKey = row.getTossPaymentKey();
            if (tossPaymentKey == null) {
                // 있으면 안 되는 데이터 이상 상황(markWaitingForDeposit이 항상 채워야 하는 값) —
                // 로그만 남기고 넘어가면 실제 계좌 상태 확인 없이 로컬만 취소로 표시되는, 이번 PR이
                // 막으려던 바로 그 문제가 재발한다(CodeRabbit 리뷰 지적). 재시도로 자연 복구되지
                // 않는 상황이라 사람이 보게 예외로 막는다.
                log.error("WAITING_FOR_DEPOSIT인데 tossPaymentKey가 없음 — 데이터 이상, 가상계좌 취소 불가. paymentId={}", paymentId);
                throw new CommonException(ErrorCode.PAYMENT_CANCELLATION_FAILED);
            }
            String cancelReason = "CANCELED".equals(targetStatus)
                    ? "결제 대상이 취소되어 가상계좌를 닫습니다."
                    : "입금 기한이 지나 가상계좌를 닫습니다.";
            tossPaymentClient.cancelVirtualAccount(tossPaymentKey, cancelReason);
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
            } catch (RestClientResponseException e) {
                // 예약 도메인이 4xx로 거부한 경우(결제 제한시간 초과·이미 취소·중복 이벤트 등,
                // ReservationPaymentCompletionService가 검증 후 던지는 것들)는 재시도해도 결과가
                // 똑같다 — "예약은 없는데 결제만 COMPLETED로 남는" 상태를 막기 위해 재시도 없이
                // 바로 자동 환불로 복구한다(2026-08-19, 이슈 #167 후속 — 주원 결정).
                if (e.getStatusCode().is4xxClientError()) {
                    log.error("예약 도메인이 결제완료 통지를 거부함({}) - 자동 환불 시도. reservationId={}, paymentId={}",
                            e.getStatusCode(), row.getReservationId(), row.getPaymentId(), e);
                    refundAfterReservationRejection(row);
                    return;
                }
                if (!retryOrGiveUp(row, attempt, e)) {
                    return;
                }
            } catch (Exception e) {
                if (!retryOrGiveUp(row, attempt, e)) {
                    return;
                }
            }
        }
    }

    /**
     * @return 재시도를 계속해도 되면 true, 이번이 마지막 시도였거나(로그만 남기고 포기)
     *         대기 중 인터럽트가 걸려 더 재시도할 수 없으면 false
     */
    private boolean retryOrGiveUp(PaymentRow row, int attempt, Exception e) {
        if (attempt == NOTIFY_MAX_ATTEMPTS) {
            // 결제 자체는 이미 성공했으므로 여기서 예외를 던져 결제 응답을 실패로 되돌리지 않는다.
            // 네트워크 장애·예약 도메인 5xx 등 일시적 문제로 보고 재시도까지 다 실패하면 로그를
            // 남겨서 운영 중 예약 확정 누락을 나중에 보정할 수 있게 한다 - 4xx(확정적 거부)와 달리
            // 여기서 자동 환불까지 하진 않는다. 짧은 재시도(200ms 간격) 동안의 일시적 장애를
            // "예약 도메인이 거부했다"로 잘못 단정해 정상 결제를 환불해버릴 위험이 더 크기 때문이다
            // (배치/스케줄러로 자동 보정하는 건 별도 과제로 남겨둠 — 알려진 한계).
            log.error("예약 도메인 결제완료 통지 {}회 재시도 모두 실패. reservationId={}, paymentId={}",
                    NOTIFY_MAX_ATTEMPTS, row.getReservationId(), row.getPaymentId(), e);
            return false;
        }
        log.warn("예약 도메인 결제완료 통지 실패({}번째 시도), 재시도한다. reservationId={}, paymentId={}",
                attempt, row.getReservationId(), row.getPaymentId(), e);
        // 대기 중 인터럽트(취소 신호) 걸리면 재시도를 더 돌리지 않고 바로 빠져나간다.
        return sleepBeforeRetry();
    }

    /**
     * 예약 도메인이 결제완료 통지를 확정적으로 거부했을 때(4xx) 자동으로 전액 환불한다.
     * 참가비(VENDOR_FEE) 결제완료 통지 실패 시의 자동환불 폴백과 같은 패턴 — actingUserId는
     * 사람이 아니라 이 메서드가 자동으로 트리거하는 환불이라 {@link #SYSTEM_ACTOR_USER_ID}를 쓴다.
     */
    private void refundAfterReservationRejection(PaymentRow row) {
        try {
            refundService.refund(row.getPaymentId(), SYSTEM_ACTOR_USER_ID,
                    new RefundRequest(RefundReason.USER_CANCEL, RequestedByDomain.PAYMENT_ADMIN));
        } catch (Exception refundEx) {
            // 환불까지 실패하면(정산 CONFIRMED 포함 등) 더는 자동으로 복구할 방법이 없어
            // 로그만 남긴다 — 운영자가 결제·예약 상태를 보고 수동으로 맞춰야 한다.
            log.error("자동 환불도 실패 — 수동 확인 필요. paymentId={}", row.getPaymentId(), refundEx);
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
