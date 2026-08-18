package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.CreateHallRequest;
import com.ms.petopia.api.fair.dto.Hall;
import com.ms.petopia.api.fair.dto.HallResponse;
import com.ms.petopia.api.fair.dto.UpdateHallRequest;
import com.ms.petopia.api.fair.mapper.BoothSlotMapper;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.fair.mapper.HallMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * halls CRUD. 부스 슬롯(도면 배치)은 별도 작업에서 다룬다.
 */
@Service
@RequiredArgsConstructor
public class HallService {

    private final HallMapper hallMapper;
    private final FairMapper fairMapper;
    private final BoothSlotMapper boothSlotMapper;
    private final StorageService storageService;
    private final FairAdminAccessGuard fairAdminAccessGuard;

    @Transactional
    public HallResponse create(Long fairId, CreateHallRequest request) {
        validateFairExists(fairId);
        fairAdminAccessGuard.checkAssigned(fairId);
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        LocalDateTime now = LocalDateTime.now();
        Hall hall = new Hall();
        hall.setFairId(fairId);
        hall.setName(request.name());
        hall.setFloorPlanImageUrl(resolveImageUrl(request.floorPlanImageObjectKey()));
        hall.setCreatedAt(now);
        hall.setUpdatedAt(now);

        hallMapper.insert(hall);
        return toResponse(hall);
    }

    @Transactional(readOnly = true)
    public List<HallResponse> getHalls(Long fairId) {
        validateFairExists(fairId);
        fairAdminAccessGuard.checkAssigned(fairId);
        return hallMapper.selectByFairId(fairId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public HallResponse getHall(Long fairId, Long hallId) {
        Hall hall = findHallInFair(fairId, hallId);
        fairAdminAccessGuard.checkAssigned(fairId);
        return toResponse(hall);
    }

    @Transactional
    public HallResponse update(Long fairId, Long hallId, UpdateHallRequest request) {
        findHallInFair(fairId, hallId);
        fairAdminAccessGuard.checkAssigned(fairId);

        Hall hall = new Hall();
        hall.setHallId(hallId);
        if (request != null) {
            // name == null이면 "변경하지 않음"(HallMapper#update의 <if> 규칙), name이
            // 빈 문자열이면 create()와 동일하게 거부한다 - null이 아니라서 그 <if>를
            // 통과해버려 빈 이름으로 그대로 저장되는 걸 막는다.
            if (request.name() != null && request.name().isBlank()) {
                throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
            }
            hall.setName(request.name());
            hall.setFloorPlanImageUrl(resolveImageUrl(request.floorPlanImageObjectKey()));
        }
        hallMapper.update(hall);

        return toResponse(hallMapper.selectById(hallId));
    }

    @Transactional
    public void delete(Long fairId, Long hallId) {
        findHallInFair(fairId, hallId);
        fairAdminAccessGuard.checkAssigned(fairId);
        // booth_slots.hall_id에는 FK가 없어(DDL 참고) 이 체크 없이 지우면 슬롯이 고아 행으로
        // 남는다 - 명시적으로 막는다.
        if (boothSlotMapper.existsByHallId(hallId)) {
            throw new CommonException(ErrorCode.HALL_HAS_BOOTH_SLOTS);
        }
        hallMapper.deleteById(hallId);
    }

    /**
     * hallId가 fairId 소속인지 함께 검증한다. 다른 행사의 홀이면 존재하지 않는 것과
     * 동일하게 404로 응답한다(다른 행사 소속 여부를 알려주지 않기 위함).
     */
    private Hall findHallInFair(Long fairId, Long hallId) {
        if (fairId == null || fairId <= 0 || hallId == null || hallId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Hall hall = hallMapper.selectById(hallId);
        if (hall == null || !hall.getFairId().equals(fairId)) {
            throw new CommonException(ErrorCode.HALL_NOT_FOUND);
        }
        return hall;
    }

    private void validateFairExists(Long fairId) {
        if (fairId == null || fairId <= 0 || fairMapper.selectById(fairId) == null) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }
    }

    /**
     * presigned 업로드로 받은 임시 객체 키를 확정(tmp → uploads)하고 공개 URL로 바꾼다.
     * 키가 없으면(새로 첨부한 이미지가 없으면) null을 그대로 반환한다 - update()에서는 이 null이
     * "변경하지 않음"으로 처리된다({@link com.ms.petopia.api.fair.mapper.HallMapper#update} 참고).
     */
    private String resolveImageUrl(String temporaryObjectKey) {
        if (temporaryObjectKey == null || temporaryObjectKey.isBlank()) {
            return null;
        }
        String confirmedKey = storageService.confirm(temporaryObjectKey, UploadPolicy.IMAGE);
        return storageService.toPublicUrl(confirmedKey);
    }

    private HallResponse toResponse(Hall hall) {
        return new HallResponse(
                hall.getHallId(),
                hall.getFairId(),
                hall.getName(),
                hall.getFloorPlanImageUrl(),
                hall.getCreatedAt(),
                hall.getUpdatedAt()
        );
    }
}
