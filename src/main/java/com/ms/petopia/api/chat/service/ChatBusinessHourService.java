package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.mapper.ChatBusinessHourMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 지금 상담사가 응대 가능한 시간대인지 판정한다.
 *
 * <p>이 판정 하나로 "AI가 먼저 답할지"가 갈린다(운영시간 밖일 때만 AI가 1회 답한다).
 * 운영시간 안이라면 사람이 답할 것이므로 AI를 부르지 않는다 - 사람 답변이 곧 올 상황에서
 * AI 답변을 끼워 넣으면 비용만 쓰고 대화가 중복된다.
 */
@Service
@RequiredArgsConstructor
public class ChatBusinessHourService {

    private final ChatBusinessHourMapper businessHourMapper;

    public boolean isWithinBusinessHours(LocalDateTime now) {
        // 휴무일이면 요일 설정과 무관하게 닫는다. 설정 두 곳이 충돌할 때 "닫힘"이 이기는
        // 편이 안전하다 - 열려 있다고 잘못 판단하면 아무도 없는 대기열에 문의가 쌓인다.
        if (businessHourMapper.existsHoliday(now.toLocalDate())) {
            return false;
        }
        return businessHourMapper.existsOpenSlot(now.getDayOfWeek().getValue(), now.toLocalTime());
    }
}
