package com.ms.petopia.api.fair.mapper;

import com.ms.petopia.api.fair.dto.BoothSlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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
     * 홀 삭제 전 부스 슬롯이 하나라도 남아있는지 확인하는 용도. booth_slots.hall_id에는
     * FK 제약이 없어서(DDL 주석 참고) 이 체크 없이 홀을 지우면 슬롯이 고아 행으로 남는다.
     */
    boolean existsByHallId(@Param("hallId") Long hallId);

    /**
     * null이 아닌 필드만 갱신한다. hall_id는 갱신 대상이 아니다(슬롯이 다른 홀로 옮겨가지 않음).
     */
    int update(BoothSlot boothSlot);

    /**
     * locked_at을 명시적으로 NULL로 되돌린다. 일반 {@link #update}는 null이 아닌 필드만
     * 갱신해서 locked_at을 NULL로 만들 방법이 없어(PATCH 방식이라 null = "안 건드림") 이
     * 전용 메서드를 따로 둔다({@code BoothSlotService#unlockBoothSlot} 참고).
     */
    int clearLock(@Param("boothSlotId") Long boothSlotId, @Param("updatedAt") LocalDateTime updatedAt);

    int deleteById(@Param("boothSlotId") Long boothSlotId);
}
