package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.BoothSlot;
import com.ms.petopia.api.fair.dto.BoothSlotItem;
import com.ms.petopia.api.fair.dto.BoothSlotResponse;
import com.ms.petopia.api.fair.dto.BulkSaveBoothSlotsRequest;
import com.ms.petopia.api.fair.dto.Hall;
import com.ms.petopia.api.fair.mapper.BoothSlotMapper;
import com.ms.petopia.api.fair.mapper.HallMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 부스 슬롯(도면 위 배치) 관리. "정의"만 다루고 "점유"(참가 신청 배정)는 다루지 않는다
 * ({@link BoothSlot} 클래스 javadoc 참고).
 */
@Service
@RequiredArgsConstructor
public class BoothSlotService {

    private final BoothSlotMapper boothSlotMapper;
    private final HallMapper hallMapper;
    private final FairTimeProvider timeProvider;

    /**
     * 특정 홀의 부스 슬롯 목록을 조회한다. 부스 배치 편집 화면이 초기 상태를 불러올 때 쓴다.
     */
    @Transactional(readOnly = true)
    public List<BoothSlotResponse> getBoothSlots(Long fairId, Long hallId) {
        findHallOrThrow(fairId, hallId);
        return boothSlotMapper.selectByHallId(hallId).stream().map(this::toResponse).toList();
    }

    /**
     * 홀의 부스 슬롯 레이아웃 전체를 요청 내용으로 맞춘다(추가/이동/삭제를 한 번에 처리).
     *
     * <p>{@code boothSlotId}가 있으면 기존 슬롯을 수정하고, 없으면 새로 만든다. 요청에
     * 포함되지 않은 기존 슬롯은 삭제한다. 단, {@code locked_at}이 채워진 슬롯(이미 참가
     * 신청이 걸려 위치·번호·가격이 확정된 슬롯)은 그 값들을 바꾸거나 삭제할 수 없고,
     * 시도하면 전체 요청을 실패시킨다({@link ErrorCode#BOOTH_SLOT_LOCKED}). memo는
     * locked 여부와 무관하게 항상 바꿀 수 있다.
     */
    @Transactional
    public List<BoothSlotResponse> bulkSave(Long fairId, Long hallId, BulkSaveBoothSlotsRequest request) {
        findHallOrThrow(fairId, hallId);
        List<BoothSlotItem> items = validateAndGetItems(request);

        Map<Long, BoothSlot> existingById = new HashMap<>();
        for (BoothSlot boothSlot : boothSlotMapper.selectByHallId(hallId)) {
            existingById.put(boothSlot.getBoothSlotId(), boothSlot);
        }

        LocalDateTime now = timeProvider.now();
        Set<Long> keptIds = new HashSet<>();
        List<BoothSlot> results = new ArrayList<>();

        for (BoothSlotItem item : items) {
            if (item.boothSlotId() != null) {
                BoothSlot existing = existingById.get(item.boothSlotId());
                if (existing == null) {
                    throw new CommonException(ErrorCode.BOOTH_SLOT_NOT_FOUND);
                }
                keptIds.add(existing.getBoothSlotId());
                results.add(updateSlot(existing, item, now));
            } else {
                results.add(insertSlot(hallId, item, now));
            }
        }

        for (BoothSlot existing : existingById.values()) {
            if (keptIds.contains(existing.getBoothSlotId())) {
                continue;
            }
            if (existing.getLockedAt() != null) {
                throw new CommonException(ErrorCode.BOOTH_SLOT_LOCKED);
            }
            boothSlotMapper.deleteById(existing.getBoothSlotId());
        }

        return results.stream().map(this::toResponse).toList();
    }

    private BoothSlot updateSlot(BoothSlot existing, BoothSlotItem item, LocalDateTime now) {
        boolean locked = existing.getLockedAt() != null;
        if (locked && corePositionChanged(existing, item)) {
            throw new CommonException(ErrorCode.BOOTH_SLOT_LOCKED);
        }

        BoothSlot updateCommand = new BoothSlot();
        updateCommand.setBoothSlotId(existing.getBoothSlotId());
        updateCommand.setMemo(item.memo());
        if (!locked) {
            updateCommand.setSlotNumber(item.slotNumber());
            updateCommand.setPosX(item.posX());
            updateCommand.setPosY(item.posY());
            updateCommand.setWidth(item.width());
            updateCommand.setHeight(item.height());
            updateCommand.setPrice(item.price());
        }
        boothSlotMapper.update(updateCommand);

        BoothSlot merged = new BoothSlot();
        merged.setBoothSlotId(existing.getBoothSlotId());
        merged.setHallId(existing.getHallId());
        merged.setSlotNumber(locked ? existing.getSlotNumber() : item.slotNumber());
        merged.setPosX(locked ? existing.getPosX() : item.posX());
        merged.setPosY(locked ? existing.getPosY() : item.posY());
        merged.setWidth(locked ? existing.getWidth() : item.width());
        merged.setHeight(locked ? existing.getHeight() : item.height());
        merged.setPrice(locked ? existing.getPrice() : item.price());
        merged.setActive(existing.getActive());
        merged.setMemo(item.memo() != null ? item.memo() : existing.getMemo());
        merged.setLockedAt(existing.getLockedAt());
        merged.setCreatedAt(existing.getCreatedAt());
        merged.setUpdatedAt(now);
        return merged;
    }

    private BoothSlot insertSlot(Long hallId, BoothSlotItem item, LocalDateTime now) {
        BoothSlot boothSlot = new BoothSlot();
        boothSlot.setHallId(hallId);
        boothSlot.setSlotNumber(item.slotNumber());
        boothSlot.setPosX(item.posX());
        boothSlot.setPosY(item.posY());
        boothSlot.setWidth(item.width());
        boothSlot.setHeight(item.height());
        boothSlot.setPrice(item.price());
        boothSlot.setMemo(item.memo());
        boothSlot.setActive(true);
        boothSlot.setCreatedAt(now);
        boothSlot.setUpdatedAt(now);
        boothSlotMapper.insert(boothSlot);
        return boothSlot;
    }

    private boolean corePositionChanged(BoothSlot existing, BoothSlotItem item) {
        return !Objects.equals(existing.getSlotNumber(), item.slotNumber())
                || bigDecimalChanged(existing.getPosX(), item.posX())
                || bigDecimalChanged(existing.getPosY(), item.posY())
                || bigDecimalChanged(existing.getWidth(), item.width())
                || bigDecimalChanged(existing.getHeight(), item.height())
                || !Objects.equals(existing.getPrice(), item.price());
    }

    private boolean bigDecimalChanged(BigDecimal existingValue, BigDecimal newValue) {
        if (existingValue == null || newValue == null) {
            return true;
        }
        return existingValue.compareTo(newValue) != 0;
    }

    private List<BoothSlotItem> validateAndGetItems(BulkSaveBoothSlotsRequest request) {
        if (request == null || request.slots() == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        List<BoothSlotItem> items = request.slots();

        Set<String> slotNumbers = new HashSet<>();
        for (BoothSlotItem item : items) {
            validateItem(item);
            if (!slotNumbers.add(item.slotNumber())) {
                throw new CommonException(ErrorCode.BOOTH_SLOT_DUPLICATE_NUMBER);
            }
        }
        return items;
    }

    private void validateItem(BoothSlotItem item) {
        if (item == null || isBlank(item.slotNumber())
                || item.posX() == null || item.posY() == null
                || item.width() == null || item.height() == null
                || item.price() == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (item.price() < 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (isOutOfUnitRange(item.posX()) || isOutOfUnitRange(item.posY())
                || isOutOfUnitRange(item.width()) || isOutOfUnitRange(item.height())) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private boolean isOutOfUnitRange(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * hallId가 fairId 소속인지 함께 검증한다. HallService의 동일 검증과 로직이 겹치지만,
     * 서비스 간 의존을 만들지 않기 위해 각자 둔다(FairService/HallService도 같은 방식).
     */
    private void findHallOrThrow(Long fairId, Long hallId) {
        if (fairId == null || fairId <= 0 || hallId == null || hallId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Hall hall = hallMapper.selectById(hallId);
        if (hall == null || !hall.getFairId().equals(fairId)) {
            throw new CommonException(ErrorCode.HALL_NOT_FOUND);
        }
    }

    private BoothSlotResponse toResponse(BoothSlot boothSlot) {
        return new BoothSlotResponse(
                boothSlot.getBoothSlotId(),
                boothSlot.getHallId(),
                boothSlot.getSlotNumber(),
                boothSlot.getPosX(),
                boothSlot.getPosY(),
                boothSlot.getWidth(),
                boothSlot.getHeight(),
                boothSlot.getPrice(),
                boothSlot.getActive(),
                boothSlot.getMemo(),
                boothSlot.getLockedAt(),
                boothSlot.getCreatedAt(),
                boothSlot.getUpdatedAt()
        );
    }
}
