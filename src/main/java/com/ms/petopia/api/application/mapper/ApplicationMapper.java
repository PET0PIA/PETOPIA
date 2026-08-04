package com.ms.petopia.api.application.mapper;

import com.ms.petopia.api.application.dto.response.BoothSlotLockStatusResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ApplicationMapper {

    // 특정 행사의 활성 부스 슬롯 전체 + 잠금 상태 조회
    List<BoothSlotLockStatusResponse> selectBoothSlotsWithLockStatus(@Param("fairId") Long fairId);

    // 행사 존재 확인
    boolean existsFair(@Param("fairId") Long fairId);

}
