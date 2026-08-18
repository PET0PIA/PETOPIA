package com.ms.petopia.api.fair.mapper;

import com.ms.petopia.api.fair.dto.Hall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * halls 테이블 매퍼. XML은 {@code mapper/fair/HallMapper.xml}에 있다.
 */
@Mapper
public interface HallMapper {

    int insert(Hall hall);

    Hall selectById(@Param("hallId") Long hallId);

    /**
     * 특정 행사의 홀 목록을 조회한다. 화면에서 "A홀, 1홀 ..." 탭/목록을 구성할 때 쓴다.
     */
    List<Hall> selectByFairId(@Param("fairId") Long fairId);

    /**
     * null이 아닌 필드만 갱신한다. fair_id는 갱신 대상이 아니다(홀이 다른 행사로 옮겨가지 않음).
     */
    int update(Hall hall);

    int deleteById(@Param("hallId") Long hallId);

    /**
     * 부스 슬롯 일괄저장용 낙관적 락. hall_id와 booth_layout_version이 동시에 일치할
     * 때만 버전을 1 증가시킨다. 반환값이 0이면(영향받은 행 없음) 그 사이 다른 저장이
     * 있었다는 뜻이라 호출부(BoothSlotService)가 버전 충돌로 처리해야 한다.
     */
    int bumpBoothLayoutVersion(@Param("hallId") Long hallId, @Param("expectedVersion") Long expectedVersion);
}
