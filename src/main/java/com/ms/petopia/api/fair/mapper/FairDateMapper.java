package com.ms.petopia.api.fair.mapper;

import com.ms.petopia.api.fair.dto.FairDate;
import com.ms.petopia.api.fair.dto.FairDateWithStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * fair_dates 테이블 매퍼. XML은 {@code mapper/fair/FairDateMapper.xml}에 있다.
 */
@Mapper
public interface FairDateMapper {

    int insert(FairDate fairDate);

    FairDate selectById(@Param("fairDateId") Long fairDateId);

    /** 같은 행사에 같은 운영 날짜가 이미 있는지 확인한다(생성 시 중복 검사용). 없으면 null. */
    FairDate selectByFairIdAndDate(@Param("fairId") Long fairId, @Param("operationDate") LocalDate operationDate);

    /** 단건을 예약 집계와 함께 조회한다. 생성·수정 응답을 만들 때 쓴다. */
    FairDateWithStats selectByIdWithStats(@Param("fairDateId") Long fairDateId);

    /** 특정 행사의 운영일 목록을 예약 집계와 함께 조회한다. FairDateResponse 참고. */
    List<FairDateWithStats> selectByFairIdWithStats(@Param("fairId") Long fairId);

    /** capacity/entry_start_time/entry_end_time/updated_at을 갱신한다. operation_date는 갱신 대상이 아니다. */
    int update(FairDate fairDate);

    int deleteById(@Param("fairDateId") Long fairDateId);
}
