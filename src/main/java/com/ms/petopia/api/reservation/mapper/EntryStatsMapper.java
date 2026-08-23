package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.PublicEntryStatsRow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EntryStatsMapper {

    /** 서비스 전체 입장 집계(고유 방문자 수, 함께 입장한 고유 반려동물 수)를 한 번에 조회한다. */
    PublicEntryStatsRow selectPublicEntryStats();
}
