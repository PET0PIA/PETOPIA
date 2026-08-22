package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.BoothLayoutResponse;
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
    private final FairAdminAccessGuard fairAdminAccessGuard;

    /**
     * 특정 홀의 부스 슬롯 목록을 조회한다. 부스 배치 편집 화면이 초기 상태를 불러올 때 쓴다.
     * 응답에 담긴 {@code boothLayoutVersion}은 다음 일괄저장 요청에 그대로 실어 보내야 한다.
     */
    @Transactional(readOnly = true)
    public BoothLayoutResponse getBoothSlots(Long fairId, Long hallId) {
        Hall hall = findHallOrThrow(fairId, hallId);
        fairAdminAccessGuard.checkAssigned(fairId);
        List<BoothSlotResponse> slots = boothSlotMapper.selectByHallId(hallId).stream().map(this::toResponse).toList();
        return new BoothLayoutResponse(slots, hall.getBoothLayoutVersion());
    }

    /**
     * 홀의 부스 슬롯 레이아웃 전체를 요청 내용으로 맞춘다(추가/이동/삭제를 한 번에 처리).
     *
     * <p>{@code boothSlotId}가 있으면 기존 슬롯을 수정하고, 없으면 새로 만든다. 요청에
     * 포함되지 않은 기존 슬롯은 삭제한다. 단, {@code locked_at}이 채워진 슬롯(이미 참가
     * 신청이 걸려 위치·번호·가격이 확정된 슬롯)은 그 값들을 바꾸거나 삭제할 수 없고,
     * 시도하면 전체 요청을 실패시킨다({@link ErrorCode#BOOTH_SLOT_LOCKED}). memo는
     * locked 여부와 무관하게 항상 바꿀 수 있다.
     *
     * <p>동시 편집 보호: 이 API는 현재 레이아웃을 통째로 읽어 diff 후 반영하는 방식이라,
     * 검증 없이 그대로 두면 뒤늦게 저장된 요청이 그 사이의 다른 저장 내용을 조용히
     * 덮어쓰거나(특히 삭제) 버릴 수 있다. 그래서 저장 전에 {@code request.expectedVersion()}과
     * halls.booth_layout_version을 낙관적 락으로 비교·증가시키고, 그 사이 다른 저장이
     * 있었다면({@link HallMapper#bumpBoothLayoutVersion} 영향받은 행 0건) 아무것도 바꾸지
     * 않고 {@link ErrorCode#BOOTH_LAYOUT_VERSION_CONFLICT}를 던진다.
     */
    @Transactional
    public BoothLayoutResponse bulkSave(Long fairId, Long hallId, BulkSaveBoothSlotsRequest request) {
        findHallOrThrow(fairId, hallId);
        fairAdminAccessGuard.checkAssigned(fairId);
        List<BoothSlotItem> items = validateAndGetItems(request);

        long newVersion = bumpVersionOrThrow(hallId, request.expectedVersion());

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

        List<BoothSlotResponse> responses = results.stream().map(this::toResponse).toList();
        return new BoothLayoutResponse(responses, newVersion);
    }

    /**
     * 부스 슬롯을 잠근다(locked_at 설정) - 이 슬롯 배치(위치·번호·가격)를 더 이상 편집할 수
     * 없게 한다. 이미 잠긴 슬롯을 다시 호출하면 아무것도 바꾸지 않고 조용히 넘어간다(멱등).
     *
     * <p><b>호출 시점</b>: application 도메인은 이미 신청 제출 순간(PENDING_REVIEW)부터 그
     * 슬롯을 "잠김"으로 취급한다({@code ApplicationMapper#selectBoothSlotsWithLockStatus}가
     * application_slot에 PENDING_REVIEW/PAYMENT_PENDING/CONFIRMED 신청이 걸려있는지를 실시간
     * EXISTS로 판단 - 승인 시점이 아니라 제출 시점부터다). Fair 쪽 locked_at도 그 판단과
     * 어긋나지 않으려면 같은 시점(신청 제출, application_slot 저장 시)에 걸어야 한다.
     *
     * <p>이 메서드는 "잠그는 능력"만 제공하고, 실제 호출 배선은 application 도메인 몫이다
     * ({@link com.ms.petopia.api.fair.service.FairCancelRefundOrchestrationService}가
     * PaymentService를 직접 호출하는 것과 동일하게 빈 주입으로 호출하면 된다). 신청이 반려·
     * 취소돼 활성 신청이 없어지면 {@link #unlockBoothSlot}으로 반드시 되돌려줘야 한다 - 그래야
     * Fair 관리자가 그 부스를 다시 배치 편집할 수 있다.
     */
    @Transactional
    public void lockBoothSlot(Long hallId, Long boothSlotId) {
        BoothSlot existing = findSlotInHallOrThrow(hallId, boothSlotId);
        if (existing.getLockedAt() != null) {
            return;
        }

        BoothSlot update = new BoothSlot();
        update.setBoothSlotId(boothSlotId);
        update.setLockedAt(timeProvider.now());
        update.setUpdatedAt(timeProvider.now());
        boothSlotMapper.update(update);
    }

    /**
     * 부스 슬롯 잠금을 해제한다(locked_at을 NULL로 되돌림). {@link #lockBoothSlot}과 쌍을
     * 이룬다 - 그 슬롯에 걸려있던 신청이 반려·취소되어 더 이상 활성 신청이 없어졌을 때
     * 호출한다. 이미 풀려있으면(locked_at이 이미 NULL) 아무것도 바꾸지 않고 조용히
     * 넘어간다(멱등).
     *
     * <p>일반 {@link BoothSlotMapper#update}는 null 필드를 건너뛰는 PATCH 방식이라 locked_at을
     * NULL로 만들 수 없어서, 전용 {@link BoothSlotMapper#clearLock}을 쓴다.
     *
     * <p>호출 시점 배선은 {@link #lockBoothSlot}과 동일하게 application 도메인 몫이다.
     */
    @Transactional
    public void unlockBoothSlot(Long hallId, Long boothSlotId) {
        BoothSlot existing = findSlotInHallOrThrow(hallId, boothSlotId);
        if (existing.getLockedAt() == null) {
            return;
        }

        boothSlotMapper.clearLock(boothSlotId, timeProvider.now());
    }

    private BoothSlot findSlotInHallOrThrow(Long hallId, Long boothSlotId) {
        if (hallId == null || hallId <= 0 || boothSlotId == null || boothSlotId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        BoothSlot slot = boothSlotMapper.selectById(boothSlotId);
        if (slot == null || !slot.getHallId().equals(hallId)) {
            throw new CommonException(ErrorCode.BOOTH_SLOT_NOT_FOUND);
        }
        return slot;
    }

    /**
     * hall_id + booth_layout_version을 WHERE 절에 함께 건 조건부 UPDATE로 버전을
     * 원자적으로 검증·증가시킨다. DB 레벨에서 처리하므로 "읽고 나서 비교" 방식과 달리
     * 두 트랜잭션이 동시에 같은 버전을 보고 둘 다 통과해버리는 경쟁 상태가 없다.
     */
    private long bumpVersionOrThrow(Long hallId, Long expectedVersion) {
        if (expectedVersion == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        int updated = hallMapper.bumpBoothLayoutVersion(hallId, expectedVersion);
        if (updated == 0) {
            throw new CommonException(ErrorCode.BOOTH_LAYOUT_VERSION_CONFLICT);
        }
        return expectedVersion + 1;
    }

    private BoothSlot updateSlot(BoothSlot existing, BoothSlotItem item, LocalDateTime now) {
        boolean locked = existing.getLockedAt() != null;
        if (locked && corePositionChanged(existing, item)) {
            throw new CommonException(ErrorCode.BOOTH_SLOT_LOCKED);
        }

        BoothSlot updateCommand = new BoothSlot();
        updateCommand.setBoothSlotId(existing.getBoothSlotId());
        updateCommand.setMemo(item.memo());
        // DB의 NOW() 대신 앱이 정한 시각을 명시적으로 실어 보낸다 - 그래야 이 저장이
        // 응답으로 돌려주는 updatedAt(now)과 실제 DB에 박히는 값이 항상 일치한다
        // (이전엔 SQL이 updated_at = NOW()를 써서 앱 서버와 DB 서버의 시계가 어긋나면
        // 응답이 실제 저장값과 달라질 수 있었다).
        updateCommand.setUpdatedAt(now);
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
        // 같은 boothSlotId를 두 번 이상 참조하면 뒤 항목이 앞 항목의 update를 덮어써 버려서
        // (existingById 맵은 갱신되지 않으니 둘 다 "원본 existing" 기준으로 처리됨) DB에는
        // 마지막 항목만 반영되는데 응답에는 두 항목이 다 담기는 불일치가 생긴다. 그래서
        // slotNumber 중복과 같은 자리에서 미리 막는다.
        Set<Long> referencedSlotIds = new HashSet<>();
        for (BoothSlotItem item : items) {
            validateItem(item);
            if (!slotNumbers.add(item.slotNumber())) {
                throw new CommonException(ErrorCode.BOOTH_SLOT_DUPLICATE_NUMBER);
            }
            if (item.boothSlotId() != null && !referencedSlotIds.add(item.boothSlotId())) {
                throw new CommonException(ErrorCode.BOOTH_SLOT_DUPLICATE_REFERENCE);
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
        // 가격은 필수 입력이라 0도 막는다. 프론트가 빈 칸을 내부적으로 0으로 들고 있어서
        // (BoothLayoutEditPage.tsx) < 0만 막으면 빈 채로 저장해도 API 직접 호출 시 통과한다.
        if (item.price() <= 0) {
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
    private Hall findHallOrThrow(Long fairId, Long hallId) {
        if (fairId == null || fairId <= 0 || hallId == null || hallId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Hall hall = hallMapper.selectById(hallId);
        if (hall == null || !hall.getFairId().equals(fairId)) {
            throw new CommonException(ErrorCode.HALL_NOT_FOUND);
        }
        return hall;
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
