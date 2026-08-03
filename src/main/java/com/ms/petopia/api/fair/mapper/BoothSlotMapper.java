package com.ms.petopia.api.fair.mapper;

import com.ms.petopia.api.fair.dto.BoothSlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * booth_slots 테이블 매퍼. XML은 {@code mapper/fair/BoothSlotMapper.xml}에 있다.
 *
 * <p>일괄 저장(추가/이동/삭제 diff 처리) 같은 API 전용 로직은 여기 넣지 않는다.
 * "부스 슬롯 일괄 저장 API 구현" 작업에서 이 기본 CRUD를 조합해서 쓴다.
 */
@Mapper
public interface BoothSlotMapper {

    int insert(BoothSlot boothSlot);

    BoothSlot selectById(@Param("boothSlotId") Long boothSlotId);

    /**
     * 특정 홀의 부스 슬롯 목록을 조회한다. 부스 배치 편집 화면과 도메인4의 점유상태
     * 오버레이가 이 목록을 기준으로 그려진다.
     */
    List<BoothSlot> selectByHallId(@Param("hallId") Long hallId);

    /**
     * null이 아닌 필드만 갱신한다. hall_id는 갱신 대상이 아니다(슬롯이 다른 홀로 옮겨가지 않음).
     */
    int update(BoothSlot boothSlot);

    int deleteById(@Param("boothSlotId") Long boothSlotId);
}
