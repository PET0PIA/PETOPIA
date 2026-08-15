package com.ms.petopia.api.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 요청자 단위 AI 사용량 제한.
 *
 * <p><b>대화당 한도만으로는 상한이 되지 못한다.</b> {@code ai_answer_count}는 대화 행에 붙어
 * 있어서, 사용자가 상담을 종료하고 새 문의를 시작하면 0부터 다시 센다. 즉 "3회 뒤 잠금"
 * 안내를 받고도 종료 버튼만 누르면 계속 쓸 수 있다. 실제 상한을 만드는 건 여기다.
 *
 * <p>그래서 창을 둘로 나눈다.
 * <ul>
 *   <li><b>1시간 창</b> - 종료·재시작 우회를 막는다. 대화당 한도와 같은 수라서, 한 대화에서
 *       다 쓰면 그 시간 안에는 새 대화를 열어도 AI가 붙지 않는다. 잠금 안내가 실제로 지켜진다.</li>
 *   <li><b>1일 창</b> - 시간창을 시간마다 새로 채워 쓰는 경우(하루 24×3)를 막는 백스톱.</li>
 * </ul>
 *
 * <p>한도를 넘겨도 <b>오류를 내지 않는다.</b> AI를 건너뛰고 상담사 대기열로 보낼 뿐이다.
 * 사용자 입장에서는 "자동 답변이 안 붙었다"일 뿐 문의는 정상 접수된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAiRateLimiter {

    private static final String HOURLY_PREFIX = "chat:ai-quota:h:";
    private static final String DAILY_PREFIX = "chat:ai-quota:d:";

    /**
     * 1시간 허용량. {@code ChatConversationService.AI_ANSWER_LIMIT}과 같은 값으로 맞춘다 -
     * 이 값이 더 크면 종료·재시작 우회가 다시 열리고, 더 작으면 한 대화조차 끝까지
     * 답하지 못하고 중간에 끊긴다.
     */
    private static final int HOURLY_LIMIT = 3;
    private static final Duration HOURLY_WINDOW = Duration.ofHours(1);

    /** 하루 백스톱. 정상 사용자가 이만큼 쓸 일은 없고, 넘으면 남용으로 본다. */
    private static final int DAILY_LIMIT = 15;
    private static final Duration DAILY_WINDOW = Duration.ofDays(1);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * @param requesterKey 로그인 사용자면 user id, 아니면 게스트 키
     * @return 이번 호출을 허용할지
     */
    public boolean tryConsume(String requesterKey) {
        if (requesterKey == null || requesterKey.isBlank()) {
            // 요청자를 특정할 수 없으면 한도를 셀 수도 없다. 세지 못하는 호출은 허용하지 않는다.
            return false;
        }

        try {
            // 두 창을 모두 올린 뒤 함께 판정한다. 하나가 막혔다고 다른 하나를 안 올리면,
            // 거부된 시도가 기록되지 않아 재시도로 창을 갉아먹을 수 있다.
            boolean hourlyOk = increment(HOURLY_PREFIX + requesterKey, HOURLY_WINDOW) <= HOURLY_LIMIT;
            boolean dailyOk = increment(DAILY_PREFIX + requesterKey, DAILY_WINDOW) <= DAILY_LIMIT;
            return hourlyOk && dailyOk;
        } catch (RuntimeException e) {
            // Redis 장애로 한도를 셀 수 없을 때는 허용하지 않는다. 상담은 상담사 대기로
            // 이어지므로 사용자 피해는 없고, 비용이 새어나가는 쪽을 막는 편이 낫다.
            log.warn("AI 사용량 확인 실패. 이번 문의는 AI를 건너뛴다.", e);
            return false;
        }
    }

    private long increment(String key, Duration window) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // 첫 증가에만 TTL을 건다. 매번 걸면 계속 쓰는 사용자의 창이 영원히 갱신돼
            // 한도가 사실상 사라진다.
            stringRedisTemplate.expire(key, window);
        }
        return count != null ? count : Long.MAX_VALUE;
    }
}
