package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.CreateFairApplicationRequest;
import com.ms.petopia.api.fair.dto.CreateFairApplicationResponse;
import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.FairApplicationDetailResponse;
import com.ms.petopia.api.fair.dto.FairReviewDecision;
import com.ms.petopia.api.fair.dto.FairStatus;
import com.ms.petopia.api.fair.dto.PublishFairResponse;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationRequest;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationResponse;
import com.ms.petopia.api.fair.dto.UpdateFairApplicationRequest;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.auth.service.AdminAccountService;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FairService {

    /**
     * 승인 시 개설비 결제 기한. 실제 정책이 확정되기 전까지 7일로 고정한다.
     * TODO 정책(결제 기한 일수) 확정되면 상수를 교체하거나 행사별 설정으로 옮긴다.
     */
    private static final Duration PAYMENT_DUE_PERIOD = Duration.ofDays(7);

    /**
     * 공개(publish)를 허용하는 상태. 심사 승인 이후(PAYMENT_PENDING~IN_PROGRESS)에만 공개할 수 있고,
     * 심사 전(RECEIVED)이거나 더 이상 진행되지 않는 상태(REJECTED/EXPIRED/ENDED)는 제외한다.
     */
    private static final Set<FairStatus> PUBLISHABLE_STATUSES =
            EnumSet.of(FairStatus.PAYMENT_PENDING, FairStatus.PREPARING, FairStatus.IN_PROGRESS);

    private final FairMapper fairMapper;
    private final FairTimeProvider timeProvider;
    private final StorageService storageService;
    private final AdminAccountService adminAccountService;
    private final NotificationService notificationService;

    /**
     * 행사 신청서를 등록한다. 심사 전 상태이므로 status는 채우지 않고 DDL 기본값(RECEIVED)에
     * 맡긴다({@code FairMapper.xml}의 insert 참고).
     */
    @Transactional
    public CreateFairApplicationResponse createApplication(Long userId, CreateFairApplicationRequest request) {
        validateRequest(userId, request);

        LocalDateTime now = timeProvider.now();

        Fair fair = new Fair();
        fair.setApplicantUserId(userId);
        fair.setName(request.name());
        fair.setDescription(request.description());
        fair.setCategory(request.category());
        fair.setPosterImageUrl(resolveImageUrl(request.posterImageObjectKey()));
        fair.setNoticeText(request.noticeText());
        fair.setPlaceName(request.placeName());
        fair.setAddress(request.address());
        fair.setIndoorOutdoor(request.indoorOutdoor());
        fair.setVendorRecruitStartDate(request.vendorRecruitStartDate());
        fair.setVendorRecruitEndDate(request.vendorRecruitEndDate());
        fair.setReservationStartDate(request.reservationStartDate());
        fair.setReservationEndDate(request.reservationEndDate());
        fair.setOperationStartDate(request.operationStartDate());
        fair.setOperationEndDate(request.operationEndDate());
        fair.setReservationFee(request.reservationFee());
        fair.setReservationCancelDeadlineHours(request.reservationCancelDeadlineHours());
        fair.setReservationChangeDeadlineHours(request.reservationChangeDeadlineHours());
        fair.setManagerName(request.managerName());
        fair.setManagerPhone(request.managerPhone());
        fair.setManagerEmail(request.managerEmail());
        fair.setCreatedAt(now);
        fair.setUpdatedAt(now);

        fairMapper.insert(fair);

        return new CreateFairApplicationResponse(
                fair.getFairId(),
                fair.getName(),
                FairStatus.RECEIVED.name(),
                fair.getCreatedAt()
        );
    }

    /**
     * 신청서 상세를 조회한다. 관리자 검토 화면 등에서 검토 전 내용을 보여줄 때 쓴다.
     * managerPhone/managerEmail을 그대로 반환하므로 호출자 신원을 요구한다 - 다만 인증
     * 도메인 완성 전까지는 신청자 본인/SUPER_ADMIN 여부를 세분화해서 검증하지 못하고
     * requesterId가 유효한 값인지만 확인한다.
     *
     * <p><b>리스크 등급: 높음</b> - 이 API는 위조된 X-User-Id로도 PII(managerPhone/
     * managerEmail)를 조회해갈 수 있다. createApplication/review/publish 같은 쓰기
     * API는 결과가 DB에 남아 사후 감사로그로 추적 가능하지만, 이 API는 읽기라 위조된
     * 헤더로 조회당하는 순간 데이터가 유출되고 사후에 막을 방법이 없다(되돌릴 수 없음).
     * 그래서 인증 도메인 작업 우선순위에서 다른 신청서 API보다 먼저 다뤄야 한다.
     * 접근 로그(누가 어떤 fairId를 조회했는지)라도 남기는 절충안을 검토했으나, 여기
     * 쓸 ActionType(예: FAIR_APPLICATION_VIEWED)이 audit 도메인 소유 enum에 아직 없어
     * 별도 확인 후 추가해야 한다 - 이번 범위에서는 보류.
     */
    @Transactional(readOnly = true)
    public FairApplicationDetailResponse getApplication(Long fairId, Long requesterId) {
        if (requesterId == null || requesterId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Fair fair = findFairOrThrow(fairId);
        return toDetailResponse(fair);
    }

    /**
     * 신청서를 수정(재제출)한다. RECEIVED(심사 대기) 또는 REJECTED(반려) 상태에서만 가능하고,
     * 본인이 신청한 행사만 수정할 수 있다(신청자 본인 여부는 requesterId가 fairs.applicant_user_id와
     * 같은지로 판단 - 인증 도메인 완성 전까지는 헤더로 받은 requesterId를 그대로 신뢰한다).
     *
     * <p>REJECTED였던 신청서는 이 수정이 성공하는 순간 RECEIVED로 되돌아가 다시 심사
     * 대기열에 선다({@link FairMapper#updateApplication} 참고, 이전 반려 사유·검토자·검토일시는
     * 함께 초기화된다).
     *
     * <p>내용 필드는 PATCH 방식이라 null로 보낸 필드는 기존 값을 유지한다. 기간 검증
     * ({@code validatePeriod})은 이번 요청에 시작일·종료일이 함께 왔을 때만 적용되고, 한쪽만
     * 보내 DB에 남은 기존 값과 조합했을 때의 유효성까지는 검증하지 않는다(알려진 제한).
     *
     * <p>RECEIVED 여부는 미리 SELECT로 확인하지 않고 UPDATE의 WHERE 절이 직접 검증한다
     * ({@link #review} javadoc과 동일한 이유).
     */
    @Transactional
    public FairApplicationDetailResponse updateApplication(Long fairId, Long requesterId, UpdateFairApplicationRequest request) {
        if (requesterId == null || requesterId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        validateUpdateRequest(request);

        Fair fair = findFairOrThrow(fairId);
        if (!requesterId.equals(fair.getApplicantUserId())) {
            throw new CommonException(ErrorCode.FAIR_APPLICATION_ACCESS_DENIED);
        }

        Fair update = new Fair();
        update.setFairId(fairId);
        update.setName(request.name());
        update.setDescription(request.description());
        update.setCategory(request.category());
        update.setPosterImageUrl(resolveImageUrl(request.posterImageObjectKey()));
        update.setNoticeText(request.noticeText());
        update.setPlaceName(request.placeName());
        update.setAddress(request.address());
        update.setIndoorOutdoor(request.indoorOutdoor());
        update.setVendorRecruitStartDate(request.vendorRecruitStartDate());
        update.setVendorRecruitEndDate(request.vendorRecruitEndDate());
        update.setReservationStartDate(request.reservationStartDate());
        update.setReservationEndDate(request.reservationEndDate());
        update.setOperationStartDate(request.operationStartDate());
        update.setOperationEndDate(request.operationEndDate());
        update.setReservationFee(request.reservationFee());
        update.setReservationCancelDeadlineHours(request.reservationCancelDeadlineHours());
        update.setReservationChangeDeadlineHours(request.reservationChangeDeadlineHours());
        update.setManagerName(request.managerName());
        update.setManagerPhone(request.managerPhone());
        update.setManagerEmail(request.managerEmail());

        int updated = fairMapper.updateApplication(update);
        if (updated == 0) {
            throw new CommonException(ErrorCode.FAIR_APPLICATION_NOT_EDITABLE);
        }

        return toDetailResponse(findFairOrThrow(fairId));
    }

    /**
     * 신청서를 승인하거나 반려한다. RECEIVED 상태의 신청서만 검토할 수 있다.
     * 승인 시 상태를 PAYMENT_PENDING으로 바꾸고 개설비 결제 기한을 잡은 뒤, 행사 관리자
     * 계정을 발급한다({@link AdminAccountService#issueEventAdminAccount}). 개설비 결제
     * 완료 감지는 별도(FairTransitionService.completeDuePayments, 폴링)로 처리한다.
     *
     * <p>RECEIVED 여부는 미리 SELECT로 확인하지 않고 UPDATE의 WHERE 절이 직접 검증한다
     * ({@link FairMapper#updateReviewResult} 참고, {@code FairCancelRequestService.review()}와
     * 동일한 패턴). "확인 후 갱신" 순서로 하면 두 검토 요청이 동시에 RECEIVED를 읽어 둘 다
     * 통과해버릴 수 있는데, 조건부 UPDATE는 그 경합을 DB가 원자적으로 해소하게 해서 둘 중
     * 먼저 커밋된 하나만 실제로 반영되고 나머지는 영향 행 0건으로 실패한다.
     *
     * <p>계정 발급은 일부러 이 트랜잭션 안에서 동기로 호출한다(환불 오케스트레이션과는
     * 다른 판단). 실패해도(예: managerEmail 중복) 되돌릴 수 없는 상태가 먼저 커밋되지
     * 않는다 - 이 메서드는 조건부 UPDATE라 실패 시 전체 롤백되고 fairs.status는 그대로
     * RECEIVED로 남아, 검토자가 같은 API를 다시 호출하는 것만으로 재시도가 된다. 환불
     * 오케스트레이션은 승인 자체(취소 확정)가 이미 되돌릴 수 없어서 별도 재시도 작업이
     * 필요했던 것과 다르다.
     */
    @Transactional
    public ReviewFairApplicationResponse review(Long fairId, Long reviewerId, ReviewFairApplicationRequest request) {
        validateReviewRequest(reviewerId, request);
        // 계정 발급에 필요한 신청자 정보(managerName/managerEmail/managerPhone)도 함께 쓰므로
        // 조회해 둔다. 상태(RECEIVED) 판단은 아래 조건부 UPDATE로 넘긴다 - 이 fair 스냅샷의
        // status는 검증에 쓰지 않는다.
        Fair fair = findFairOrThrow(fairId);

        LocalDateTime now = timeProvider.now();
        boolean approved = request.decision() == FairReviewDecision.APPROVE;

        Fair update = new Fair();
        update.setFairId(fairId);
        update.setReviewedBy(reviewerId);
        update.setReviewedAt(now);
        if (approved) {
            update.setStatus(FairStatus.PAYMENT_PENDING);
            update.setPaymentDueAt(now.plus(PAYMENT_DUE_PERIOD));
        } else {
            update.setStatus(FairStatus.REJECTED);
            update.setRejectReason(request.rejectReason().trim());
        }

        int updated = fairMapper.updateReviewResult(update);
        if (updated == 0) {
            throw new CommonException(ErrorCode.FAIR_NOT_PENDING_REVIEW);
        }

        if (approved) {
            adminAccountService.issueEventAdminAccount(
                    fairId, fair.getApplicantUserId(), fair.getManagerName(), fair.getManagerEmail(), fair.getManagerPhone()
            );
        }

        Long applicantUserId = fair.getApplicantUserId();
        if (approved) {
            notifyFairReviewAfterCommit(applicantUserId, NotificationType.FAIR_APPLICATION_APPROVED,
                    "행사 신청이 승인되었습니다",
                    "개설비를 " + update.getPaymentDueAt().toLocalDate() + "까지 결제해 주세요.");
        } else {
            notifyFairReviewAfterCommit(applicantUserId, NotificationType.FAIR_APPLICATION_REJECTED,
                    "행사 신청이 반려되었습니다",
                    "반려 사유: " + update.getRejectReason());
        }

        return new ReviewFairApplicationResponse(
                fairId,
                update.getStatus().name(),
                now,
                update.getPaymentDueAt(),
                update.getRejectReason()
        );
    }

    private void notifyFairReviewAfterCommit(Long recipientUserId, NotificationType type,
                                             String title, String body) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    notificationService.save(new SaveNotificationDto.Request(
                            recipientUserId,
                            RecipientType.USER,
                            type,
                            title,
                            body,
                            null,
                            List.of(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
                            null
                    ));
                } catch (Exception e) {
                    log.error("행사 심사 알림 저장 실패. recipientUserId={}, type={}", recipientUserId, type, e);
                }
            }
        });
    }

    /**
     * 행사를 공개해 예약을 받을 수 있게 한다. reservation 도메인은
     * {@code fairs.published_at IS NOT NULL}만 보고 예약 가능 여부를 판단하므로(취소·예약기간은
     * reservation 도메인이 별도로 검증) 여기서는 published_at만 채운다.
     *
     * <p>이미 공개된 행사를 다시 호출하면 에러 없이 최초 공개 결과를 그대로 반환한다(멱등).
     */
    @Transactional
    public PublishFairResponse publish(Long fairId, Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Fair fair = findFairOrThrow(fairId);

        if (fair.getPublishedAt() != null) {
            return new PublishFairResponse(fairId, fair.getStatus().name(), fair.getPublishedAt());
        }
        if (fair.getCanceledAt() != null || !PUBLISHABLE_STATUSES.contains(fair.getStatus())) {
            throw new CommonException(ErrorCode.FAIR_NOT_PUBLISHABLE);
        }

        LocalDateTime now = timeProvider.now();
        Fair update = new Fair();
        update.setFairId(fairId);
        update.setPublishedAt(now);
        fairMapper.update(update);

        return new PublishFairResponse(fairId, fair.getStatus().name(), now);
    }

    private void validateReviewRequest(Long reviewerId, ReviewFairApplicationRequest request) {
        if (reviewerId == null || reviewerId <= 0 || request == null || request.decision() == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.decision() == FairReviewDecision.REJECT && isBlank(request.rejectReason())) {
            throw new CommonException(ErrorCode.FAIR_REJECT_REASON_REQUIRED);
        }
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

    private FairApplicationDetailResponse toDetailResponse(Fair fair) {
        return new FairApplicationDetailResponse(
                fair.getFairId(),
                fair.getApplicantUserId(),
                fair.getName(),
                fair.getDescription(),
                fair.getCategory(),
                fair.getPosterImageUrl(),
                fair.getNoticeText(),
                fair.getPlaceName(),
                fair.getAddress(),
                fair.getIndoorOutdoor(),
                fair.getVendorRecruitStartDate(),
                fair.getVendorRecruitEndDate(),
                fair.getReservationStartDate(),
                fair.getReservationEndDate(),
                fair.getOperationStartDate(),
                fair.getOperationEndDate(),
                fair.getReservationFee(),
                fair.getReservationCancelDeadlineHours(),
                fair.getReservationChangeDeadlineHours(),
                fair.getManagerName(),
                fair.getManagerPhone(),
                fair.getManagerEmail(),
                fair.getStatus() == null ? null : fair.getStatus().name(),
                fair.getRejectReason(),
                fair.getReviewedAt(),
                fair.getPaymentDueAt(),
                fair.getCreatedAt()
        );
    }

    private void validateRequest(Long userId, CreateFairApplicationRequest request) {
        if (userId == null || userId <= 0 || request == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (isBlank(request.name()) || isBlank(request.managerName()) || isBlank(request.managerEmail())) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.reservationFee() != null && request.reservationFee() < 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        validatePeriod(
                request.vendorRecruitStartDate(), request.vendorRecruitEndDate(),
                ErrorCode.FAIR_INVALID_VENDOR_RECRUIT_PERIOD
        );
        validatePeriod(
                request.reservationStartDate(), request.reservationEndDate(),
                ErrorCode.FAIR_INVALID_RESERVATION_PERIOD
        );
        validatePeriod(
                request.operationStartDate(), request.operationEndDate(),
                ErrorCode.FAIR_INVALID_OPERATION_PERIOD
        );
    }

    /**
     * updateApplication 전용 검증. createApplication과 달리 PATCH라 필드가 null일 수 있으므로
     * "필수" 대신 "보냈다면 빈 문자열이면 안 된다"만 확인한다.
     */
    private void validateUpdateRequest(UpdateFairApplicationRequest request) {
        if (request == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (isBlankIfPresent(request.name()) || isBlankIfPresent(request.managerName())
                || isBlankIfPresent(request.managerEmail())) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.reservationFee() != null && request.reservationFee() < 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        validatePeriod(
                request.vendorRecruitStartDate(), request.vendorRecruitEndDate(),
                ErrorCode.FAIR_INVALID_VENDOR_RECRUIT_PERIOD
        );
        validatePeriod(
                request.reservationStartDate(), request.reservationEndDate(),
                ErrorCode.FAIR_INVALID_RESERVATION_PERIOD
        );
        validatePeriod(
                request.operationStartDate(), request.operationEndDate(),
                ErrorCode.FAIR_INVALID_OPERATION_PERIOD
        );
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate, ErrorCode errorCode) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new CommonException(errorCode);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isBlankIfPresent(String value) {
        return value != null && value.isBlank();
    }

    /**
     * presigned 업로드로 받은 임시 객체 키를 확정(tmp → uploads)하고 공개 URL로 바꾼다.
     * 키가 없으면(이미지를 첨부하지 않았으면) null을 그대로 반환한다.
     */
    private String resolveImageUrl(String temporaryObjectKey) {
        if (isBlank(temporaryObjectKey)) {
            return null;
        }
        String confirmedKey = storageService.confirm(temporaryObjectKey, UploadPolicy.IMAGE);
        return storageService.toPublicUrl(confirmedKey);
    }
}
