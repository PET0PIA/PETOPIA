package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.CreateFairApplicationRequest;
import com.ms.petopia.api.fair.dto.CreateFairApplicationResponse;
import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.FairStatus;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FairService {

    private final FairMapper fairMapper;

    /**
     * 행사 신청서를 등록한다. 심사 전 상태이므로 status는 채우지 않고 DDL 기본값(RECEIVED)에
     * 맡긴다({@code FairMapper.xml}의 insert 참고).
     */
    @Transactional
    public CreateFairApplicationResponse createApplication(Long userId, CreateFairApplicationRequest request) {
        validateRequest(userId, request);

        LocalDateTime now = LocalDateTime.now();

        Fair fair = new Fair();
        fair.setApplicantUserId(userId);
        fair.setName(request.name());
        fair.setDescription(request.description());
        fair.setCategory(request.category());
        fair.setPosterImageUrl(request.posterImageUrl());
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
}
