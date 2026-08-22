package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.mapper.ChatBusinessHourMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 지금 상담사가 응대 가능한 시간대인지 판정한다.
 *
 * <p>이 판정 하나로 "AI가 답할지"가 갈린다 - 운영시간 밖에 들어온 질문만 자동 응대로
 * 넘어간다. 운영시간 안이라면 사람이 답할 것이므로 AI를 부르지 않는다. 사람 답변이 곧 올
 * 상황에서 자동 답변을 끼워 넣으면 두 목소리가 생기고 비용만 쓴다.
 */
@Service
@RequiredArgsConstructor
public class ChatBusinessHourService {

    private final ChatBusinessHourMapper businessHourMapper;

    /**
     * 지금 열려 있다면 오늘 몇 시에 닫는지. 닫혀 있으면 null.
     *
     * <p>"상담 가능"만 보여주면 사용자는 "지금 물어봐도 되나"까지는 알아도 "얼마나 여유가
     * 있나"는 모른다. 17:55에 문의를 시작하는 사람과 10시에 시작하는 사람은 기대가 달라야 한다.
     *
     * <p>닫혀 있을 때 다음 여는 시각을 주지 않는 이유: 그건 요일을 넘겨가며 다음 운영일을
     * 찾는 별개의 계산이고(휴무일까지 건너뛰어야 한다), 지금 화면에 필요한 정보가 아니다.
     * 운영시간 밖에는 자동 응대가 답하므로 사용자가 기다릴 이유가 없다.
     */
    public LocalTime closingTime(LocalDateTime now) {
        if (businessHourMapper.existsHoliday(now.toLocalDate())) {
            return null;
        }
        return businessHourMapper.selectOpenSlotEndTime(now.getDayOfWeek().getValue(), now.toLocalTime());
    }

    public boolean isWithinBusinessHours(LocalDateTime now) {
        // 휴무일이면 요일 설정과 무관하게 닫는다. 설정 두 곳이 충돌할 때 "닫힘"이 이기는
        // 편이 안전하다 - 열려 있다고 잘못 판단하면 아무도 없는 대기열에 문의가 쌓인다.
        if (businessHourMapper.existsHoliday(now.toLocalDate())) {
            return false;
        }
        return businessHourMapper.existsOpenSlot(now.getDayOfWeek().getValue(), now.toLocalTime());
    }
}
