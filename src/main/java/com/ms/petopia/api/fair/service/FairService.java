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
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

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
     * TODO PAYMENT_PENDING -> PREPARING 자동전이(개설비 결제 연동)가 구현되기 전까지는 실질적으로
     *      PAYMENT_PENDING 상태에서만 호출된다. 스케줄러(상태 자동전이) 작업에서 재검토한다.
     */
    private static final Set<FairStatus> PUBLISHABLE_STATUSES =
            EnumSet.of(FairStatus.PAYMENT_PENDING, FairStatus.PREPARING, FairStatus.IN_PROGRESS);

    private final FairMapper fairMapper;
    private final FairTimeProvider timeProvider;
    private final StorageService storageService;

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
     */
    @Transactional(readOnly = true)
    public FairApplicationDetailResponse getApplication(Long fairId) {
        Fair fair = findFairOrThrow(fairId);
        return toDetailResponse(fair);
    }

    /**
     * 신청서를 승인하거나 반려한다. RECEIVED 상태의 신청서만 검토할 수 있다.
     * 승인 시 상태를 PAYMENT_PENDING으로 바꾸고 개설비 결제 기한을 잡는다. 결제/계정발급
     * 연동은 별도 작업(개설비 결제·계정발급 연동)에서 이 기한을 기준으로 처리한다.
     */
    @Transactional
    public ReviewFairApplicationResponse review(Long fairId, Long reviewerId, ReviewFairApplicationRequest request) {
        validateReviewRequest(reviewerId, request);
        Fair fair = findFairOrThrow(fairId);

        if (fair.getStatus() != FairStatus.RECEIVED) {
            throw new CommonException(ErrorCode.FAIR_NOT_PENDING_REVIEW);
        }

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

        fairMapper.update(update);

        return new ReviewFairApplicationResponse(
                fairId,
                update.getStatus().name(),
                now,
                update.getPaymentDueAt(),
                update.getRejectReason()
        );
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

    private void validatePeriod(LocalDate startDate, LocalDate endDate, ErrorCode errorCode) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new CommonException(errorCode);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
