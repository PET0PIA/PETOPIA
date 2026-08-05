package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.CreateFairDateRequest;
import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.FairDate;
import com.ms.petopia.api.fair.dto.FairDateResponse;
import com.ms.petopia.api.fair.dto.FairDateWithStats;
import com.ms.petopia.api.fair.dto.UpdateFairDateRequest;
import com.ms.petopia.api.fair.mapper.FairDateMapper;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * fair_dates(운영일·정원) CRUD.
 *
 * <p>정원 축소·삭제가 기존 예약·현장예매 정책과 충돌할 수 있어도 여기서 막지 않는다 -
 * {@link FairDateResponse}의 reservedCount/onsiteSalesConfigured로 관리자 화면이 경고만
 * 보여주고, 계속 진행할지는 관리자 판단에 맡긴다.
 */
@Service
@RequiredArgsConstructor
public class FairDateService {

    private final FairDateMapper fairDateMapper;
    private final FairMapper fairMapper;

    @Transactional
    public FairDateResponse create(Long fairId, CreateFairDateRequest request) {
        Fair fair = findFairOrThrow(fairId);
        validateCreateRequest(fair, request);

        if (fairDateMapper.selectByFairIdAndDate(fairId, request.operationDate()) != null) {
            throw new CommonException(ErrorCode.FAIR_DATE_DUPLICATE);
        }

        LocalDateTime now = LocalDateTime.now();
        FairDate fairDate = new FairDate();
        fairDate.setFairId(fairId);
        fairDate.setOperationDate(request.operationDate());
        fairDate.setCapacity(request.capacity());
        fairDate.setEntryStartTime(request.entryStartTime());
        fairDate.setEntryEndTime(request.entryEndTime());
        fairDate.setCreatedAt(now);
        fairDate.setUpdatedAt(now);

        fairDateMapper.insert(fairDate);
        return toResponse(fairDateMapper.selectByIdWithStats(fairDate.getFairDateId()));
    }

    @Transactional(readOnly = true)
    public List<FairDateResponse> getFairDates(Long fairId) {
        findFairOrThrow(fairId);
        return fairDateMapper.selectByFairIdWithStats(fairId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FairDateResponse update(Long fairId, Long fairDateId, UpdateFairDateRequest request) {
        findFairDateInFair(fairId, fairDateId);
        validateUpdateRequest(request);

        FairDate update = new FairDate();
        update.setFairDateId(fairDateId);
        update.setCapacity(request.capacity());
        update.setEntryStartTime(request.entryStartTime());
        update.setEntryEndTime(request.entryEndTime());
        update.setUpdatedAt(LocalDateTime.now());
        fairDateMapper.update(update);

        return toResponse(fairDateMapper.selectByIdWithStats(fairDateId));
    }

    @Transactional
    public void delete(Long fairId, Long fairDateId) {
        findFairDateInFair(fairId, fairDateId);
        fairDateMapper.deleteById(fairDateId);
    }

    /**
     * fairDateId가 fairId 소속인지 함께 검증한다. 다른 행사의 운영일이면 존재하지 않는 것과
     * 동일하게 404로 응답한다(다른 행사 소속 여부를 알려주지 않기 위함).
     */
    private FairDate findFairDateInFair(Long fairId, Long fairDateId) {
        if (fairId == null || fairId <= 0 || fairDateId == null || fairDateId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        FairDate fairDate = fairDateMapper.selectById(fairDateId);
        if (fairDate == null || !fairDate.getFairId().equals(fairId)) {
            throw new CommonException(ErrorCode.FAIR_DATE_NOT_FOUND);
        }
        return fairDate;
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

    private void validateCreateRequest(Fair fair, CreateFairDateRequest request) {
        if (request == null || request.operationDate() == null || request.capacity() == null
                || request.entryStartTime() == null || request.entryEndTime() == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.capacity() <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!request.entryStartTime().isBefore(request.entryEndTime())) {
            throw new CommonException(ErrorCode.FAIR_DATE_INVALID_ENTRY_TIME);
        }
        validateWithinOperationPeriod(fair, request.operationDate());
    }

    private void validateUpdateRequest(UpdateFairDateRequest request) {
        if (request == null || request.capacity() == null
                || request.entryStartTime() == null || request.entryEndTime() == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.capacity() <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!request.entryStartTime().isBefore(request.entryEndTime())) {
            throw new CommonException(ErrorCode.FAIR_DATE_INVALID_ENTRY_TIME);
        }
    }

    /**
     * 운영 날짜가 행사의 운영 기간(operationStartDate~operationEndDate) 안에 있는지 확인한다.
     * 둘 중 하나라도 아직 안 정해졌으면(null) 검사를 건너뛴다 - 신청 단계에서는 운영 기간
     * 자체가 비어 있을 수 있어서다.
     */
    private void validateWithinOperationPeriod(Fair fair, LocalDate operationDate) {
        LocalDate start = fair.getOperationStartDate();
        LocalDate end = fair.getOperationEndDate();
        if (start != null && operationDate.isBefore(start)) {
            throw new CommonException(ErrorCode.FAIR_DATE_OUT_OF_OPERATION_PERIOD);
        }
        if (end != null && operationDate.isAfter(end)) {
            throw new CommonException(ErrorCode.FAIR_DATE_OUT_OF_OPERATION_PERIOD);
        }
    }

    private FairDateResponse toResponse(FairDateWithStats row) {
        return new FairDateResponse(
                row.getFairDateId(),
                row.getFairId(),
                row.getOperationDate(),
                row.getCapacity(),
                row.getEntryStartTime(),
                row.getEntryEndTime(),
                row.getReservedCount(),
                row.isOnsiteSalesConfigured(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }
}
