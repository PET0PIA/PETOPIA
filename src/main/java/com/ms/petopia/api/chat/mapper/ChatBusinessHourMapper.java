package com.ms.petopia.api.chat.mapper;

import com.ms.petopia.api.chat.dto.ChatBusinessHour;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Mapper
public interface ChatBusinessHourMapper {

    /**
     * 지금이 상담 운영시간인지.
     *
     * <p>요일·시각 판단을 SQL 한 번으로 끝낸다. 요일 값은 java.time.DayOfWeek(1=월)를 그대로
     * 넣으므로 애플리케이션에서 변환하지 않는다.
     */
    boolean existsOpenSlot(@Param("dayOfWeek") int dayOfWeek,
                           @Param("time") LocalTime time);

    /**
     * 그 요일·시각을 포함하는 운영 구간의 종료 시각. 해당 구간이 없으면 null.
     *
     * <p>{@link #existsOpenSlot}과 조건이 같아야 한다 - 한쪽은 "열렸는가"를, 다른 쪽은 "언제
     * 닫는가"를 답하는데 기준이 어긋나면 "상담 가능"이라면서 이미 지난 시각을 보여준다.
     */
    LocalTime selectOpenSlotEndTime(@Param("dayOfWeek") int dayOfWeek,
                                    @Param("time") LocalTime time);

    boolean existsHoliday(@Param("date") LocalDate date);

    List<ChatBusinessHour> selectAll();

    /**
     * 요일 하나의 운영시간을 저장한다. 요일이 UNIQUE라 INSERT ... ON DUPLICATE KEY UPDATE로
     * 신규·수정을 한 문장에 담는다 - 조회 후 분기하면 동시에 두 관리자가 저장할 때 한쪽이
     * 중복 키로 실패한다.
     */
    void upsert(ChatBusinessHour hour);
}
