package com.ms.petopia.api.chat.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * fair 도메인의 {@code FairTimeProvider}와 동일한 이유로 존재한다 - 서비스 로직에서
 * {@code LocalDateTime.now()}를 직접 호출하면 테스트에서 시각을 고정할 수 없어서다.
 *
 * <p>운영시간 판정이 이 값에 걸려 있어 챗봇에서는 특히 중요하다. 경계 시각(09:00, 18:00)
 * 테스트를 실제 시계로 돌릴 수는 없다.
 */
@Component
public class ChatTimeProvider {

    public static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    public LocalDateTime now() {
        return LocalDateTime.now(SEOUL_ZONE);
    }
}
