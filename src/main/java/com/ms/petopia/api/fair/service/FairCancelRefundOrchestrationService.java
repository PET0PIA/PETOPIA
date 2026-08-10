package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.FairCancelRefundTarget;
import com.ms.petopia.api.fair.mapper.FairCancelRefundTargetMapper;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.payment.dto.PaymentListResponse;
import com.ms.petopia.api.payment.dto.PaymentResponse;
import com.ms.petopia.api.payment.service.PaymentService;
import com.ms.petopia.api.refund.dto.RefundReason;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RequestedByDomain;
import com.ms.petopia.api.refund.service.RefundService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 행사 취소 승인 뒤 관람객 예약금·참가업체 참가비를 환불한다(개설비는 관리자 수동 처리
 * 대상이라 제외 - {@link RefundReason#OPENING_FEE_MANUAL} 참고). {@link FairCancelRefundJob}이
 * 주기적으로 호출하는 두 단계로 나뉜다.
 *
 * <p><b>왜 승인 API 응답 안에서 동기로 처리하지 않는가</b>: 처음엔 취소 승인 직후 이 도메인이
 * {@code PaymentService.getPayments()}로 대상을 조회하고 바로 {@code RefundService.refund()}를
 * 부르는 방식이었다. 근데 취소 승인은 이미 커밋된 뒤라, 그 뒤 결제 도메인 호출이 실패하면
 * (일시적 네트워크/DB 문제 등) 그 요청은 오류로 끝나고, {@code FairCancelRequestService.review()}는
 * PENDING 신청만 검토할 수 있어서 같은 API로 환불을 다시 트리거할 방법이 없었다. 그래서
 * "환불해야 할 결제"를 {@code fair_cancel_refund_targets}에 작업행으로 영속화하고, 스케줄러가
 * 반복 처리(재시도)하는 방식으로 바꿨다 - 취소 승인 API 자체는 더 이상 결제 도메인 호출
 * 성패에 영향받지 않는다.
 *
 * <p>발견(enumerate)과 처리(process) 모두 일부러 {@code @Transactional}을 달지 않는다.
 * {@link RefundService#refund}는 결제 한 건마다 자기 트랜잭션을 갖는데, 여길
 * {@code @Transactional}로 감싸면 같은 트랜잭션에 합류(REQUIRED)해버려서 한 건이라도
 * 예외를 던지면 배치 전체가 rollback-only로 표시돼 이미 처리한 나머지까지
 * {@code UnexpectedRollbackException}으로 날아간다. 결제/작업 건마다 독립된 트랜잭션(또는
 * 단일 UPDATE)으로 처리해서 한 건의 실패가 나머지에 영향을 주지 않게 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FairCancelRefundOrchestrationService {

    private static final int PAYMENT_PAGE_SIZE = 100;

    /** 예상 못한(잠재적으로 일시적인) 실패를 몇 번까지 재시도할지. 넘기면 FAILED로 포기한다. */
    private static final int MAX_ATTEMPTS = 5;

    /** 재시도해도 성공할 수 없는(즉시 FAILED로 확정할) 에러코드. */
    private static final Set<ErrorCode> TERMINAL_ERROR_CODES =
            EnumSet.of(ErrorCode.REFUND_ALREADY_PROCESSED, ErrorCode.REFUND_TARGET_NOT_REFUNDABLE);

    /** 실제 처리자를 알 수 없는 배치 컨텍스트에서 쓰는 표시값 - RefundService는 이 값을
     * 로그로만 남기고 저장하지 않는다(REFUND 테이블에 별도 컬럼 없음). */
    private static final Long SYSTEM_ACTOR_USER_ID = 0L;

    /** 자동 환불 대상 결제유형 -> 환불 사유. 개설비는 의도적으로 포함하지 않는다. */
    private static final Map<String, RefundReason> REFUNDABLE_TYPES = Map.of(
            "RESERVATION_DEPOSIT", RefundReason.FAIR_CANCEL_USER,
            "VENDOR_FEE", RefundReason.FAIR_CANCEL_VENDOR
    );

    private final FairCancelRefundTargetMapper targetMapper;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final NotificationService notificationService;
    private final FairTimeProvider timeProvider;

    /**
     * 취소됐지만 아직 발견 단계를 끝까지 완료하지 않은 행사를 찾아, COMPLETED 예약금·참가비
     * 결제를 전부 작업행으로 등록한다. 이미 등록된 결제는 유니크 제약으로 조용히 건너뛴다.
     *
     * <p>행사 하나는 모든 결제유형·모든 페이지를 예외 없이 다 훑었을 때만 완료로 기록한다
     * (참가비 결제가 0건이어도 완료로 기록됨 - "확인해서 0건"과 "아직 확인 안 함"을
     * 구분해야 하기 때문). 한 행사 처리 중 예외가 나도 그 행사만 미완료로 남기고 다음
     * 행사로 넘어간다 - 한 행사의 실패가 배치 전체나 뒤이은 {@link #processPendingTargets}
     * 호출을 막지 않게 하기 위함.
     *
     * @return 새로 등록한 작업행 수
     */
    public int enumerateTargets(int fairBatchSize) {
        List<Long> fairIds = targetMapper.selectUnenumeratedCanceledFairIds(fairBatchSize);
        int enumerated = 0;
        for (Long fairId : fairIds) {
            enumerated += enumerateFair(fairId);
        }
        return enumerated;
    }

    private int enumerateFair(Long fairId) {
        int enumerated = 0;
        try {
            for (String paymentType : REFUNDABLE_TYPES.keySet()) {
                enumerated += enumerateType(fairId, paymentType);
            }
            targetMapper.markEnumerationCompleted(fairId, timeProvider.now());
        } catch (DuplicateKeyException e) {
            // 동시 실행 등으로 이미 완료 기록이 있는 경우 - 무시한다.
        } catch (RuntimeException e) {
            // 결제 도메인 조회 실패 등 - 이 행사만 미완료로 남기고(다음 스케줄에서 처음부터
            // 재시도됨) 나머지 행사 발견과 processPendingTargets 호출은 계속 진행한다.
            log.warn("행사 취소 환불 대상 발견 실패. fairId={}", fairId, e);
        }
        return enumerated;
    }

    private int enumerateType(Long fairId, String paymentType) {
        int enumerated = 0;
        int page = 0;
        int totalPages = 1;
        while (page < totalPages) {
            PaymentListResponse response =
                    paymentService.getPayments(fairId, null, paymentType, "COMPLETED", page, PAYMENT_PAGE_SIZE);
            totalPages = response.totalPages();

            for (PaymentResponse payment : response.content()) {
                if (registerTarget(fairId, payment.paymentId(), paymentType)) {
                    enumerated++;
                }
            }
            page++;
        }
        return enumerated;
    }

    private boolean registerTarget(Long fairId, Long paymentId, String paymentType) {
        LocalDateTime now = timeProvider.now();
        FairCancelRefundTarget target = new FairCancelRefundTarget();
        target.setFairId(fairId);
        target.setPaymentId(paymentId);
        target.setPaymentType(paymentType);
        target.setCreatedAt(now);
        target.setUpdatedAt(now);
        try {
            targetMapper.insert(target);
            return true;
        } catch (DuplicateKeyException e) {
            // 이미 등록된 결제 - 발견 단계가 여러 번 돌아도 안전하게 무시한다.
            return false;
        }
    }

    /**
     * PENDING 작업을 처리한다.
     *
     * @return 이번 호출에서 새로 성공 처리한 건수
     */
    public int processPendingTargets(int batchSize) {
        List<FairCancelRefundTarget> targets = targetMapper.selectPendingForUpdate(batchSize);
        int completed = 0;
        for (FairCancelRefundTarget target : targets) {
            if (process(target)) {
                completed++;
            }
        }
        return completed;
    }

    private boolean process(FairCancelRefundTarget target) {
        RefundReason reason = REFUNDABLE_TYPES.get(target.getPaymentType());
        LocalDateTime now = timeProvider.now();
        Long targetId = target.getFairCancelRefundTargetId();

        try {
            refundService.refund(target.getPaymentId(), SYSTEM_ACTOR_USER_ID,
                    new RefundRequest(reason, RequestedByDomain.FAIR));
            boolean completed = targetMapper.markCompleted(targetId, now) == 1;
            if (completed) {
                notifyFairCanceled(target);
            }
            return completed;
        } catch (CommonException e) {
            if (TERMINAL_ERROR_CODES.contains(e.getErrorCode())) {
                targetMapper.markFailed(targetId, truncate(e.getMessage()), now);
            } else {
                targetMapper.markRetryOrGiveUp(targetId, truncate(e.getMessage()), MAX_ATTEMPTS, now);
            }
            return false;
        } catch (RuntimeException e) {
            // 결제 도메인 쪽 데이터 접근 예외 등 예상 못한 실패 - 잠재적으로 일시적이라 재시도
            // 대상으로 남긴다(시도 횟수가 MAX_ATTEMPTS에 도달하면 그때 FAILED로 포기한다).
            log.warn("행사 취소 환불 처리 실패. targetId={}, fairId={}, paymentId={}",
                    targetId, target.getFairId(), target.getPaymentId(), e);
            targetMapper.markRetryOrGiveUp(targetId, truncate(e.getMessage()), MAX_ATTEMPTS, now);
            return false;
        }
    }

    // 환불 성공 건마다 해당 결제자에게 행사 취소 알림을 보낸다.
    // 결제 타입으로 수신자 역할을 구분한다(관람객=USER, 확정 업체=VENDOR).
    private void notifyFairCanceled(FairCancelRefundTarget target) {
        try {
            PaymentResponse payment = paymentService.getPayment(target.getPaymentId());
            RecipientType recipientType = "VENDOR_FEE".equals(target.getPaymentType())
                    ? RecipientType.VENDOR
                    : RecipientType.USER;
            notificationService.save(new SaveNotificationDto.Request(
                    payment.payerUserId(),
                    recipientType,
                    NotificationType.FAIR_CANCELED,
                    "행사가 취소되었습니다",
                    "참가하셨던 행사가 취소되어 환불이 처리되었습니다.",
                    null,
                    List.of(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
                    null
            ));
        } catch (Exception e) {
            log.error("행사 취소 알림 저장 실패. paymentId={}, fairId={}",
                    target.getPaymentId(), target.getFairId(), e);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
