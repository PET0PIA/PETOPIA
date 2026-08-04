package com.ms.petopia.api.application.service;

import com.ms.petopia.api.application.dto.response.BoothSlotLockStatusResponse;
import com.ms.petopia.api.application.mapper.ApplicationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationMapper applicationMapper;

    // 부스 슬롯 목록 + 잠금 상태 조회
    public List<BoothSlotLockStatusResponse> getBoothSlots(Long fairId) {

        if(!applicationMapper.existsFair(fairId)) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }

        return applicationMapper.selectBoothSlotsWithLockStatus(fairId);

    }

}
