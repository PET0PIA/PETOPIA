package com.ms.petopia.api.chat.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 상담 이벤트를 모든 인스턴스로 퍼뜨린다.
 *
 * <p>채널을 대화별로 쪼개지 않고 하나로 둔다. 대화마다 채널을 만들면 구독/해지가 연결
 * 수명과 얽혀 관리 지점이 늘어나는데, 상담 트래픽 규모에서는 한 채널로 받아 대화 ID로
 * 거르는 편이 훨씬 단순하고 충분히 싸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    public static final String CHANNEL = "chat:events";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 트랜잭션이 커밋된 뒤에 발행한다.
     *
     * <p>커밋 전에 발행하면 롤백된 메시지가 화면에 남는다. 사용자는 답변을 봤는데 DB에는
     * 없는 상태가 되고, 새로고침하면 사라진다 - 재현도 설명도 어려운 종류의 버그다.
     * 트랜잭션 밖에서 호출되면 즉시 발행한다(타이핑 신호처럼 DB를 건드리지 않는 이벤트).
     */
    public void publish(ChatStreamEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
            return;
        }
        send(event);
    }

    private void send(ChatStreamEvent event) {
        try {
            stringRedisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (JacksonException e) {
            // 실시간 전달에 실패해도 상담 자체는 계속돼야 한다. 클라이언트는 폴링 폴백과
            // 재연결 시 커서 조회로 놓친 메시지를 복구한다.
            log.warn("상담 이벤트 직렬화 실패. conversationId={}, type={}",
                    event.conversationId(), event.type(), e);
        } catch (RuntimeException e) {
            log.warn("상담 이벤트 발행 실패. conversationId={}, type={}",
                    event.conversationId(), event.type(), e);
        }
    }
}
