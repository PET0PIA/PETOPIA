package com.ms.petopia.api.chat.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Redis에서 받은 상담 이벤트를 이 인스턴스의 SSE 연결로 흘려보낸다.
 *
 * <p>발행자는 자기 자신도 포함해 모든 인스턴스로 보낸다. 발행한 인스턴스만 로컬로 바로
 * 전달하고 나머지를 Redis로 보내는 식으로 나누면 경로가 둘이 되어, 한쪽에만 있는 버그가
 * "특정 태스크에 붙은 사용자에게만 안 보인다"는 형태로 나타난다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final ChatEmitterRegistry emitterRegistry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatStreamEvent event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), ChatStreamEvent.class);
            emitterRegistry.dispatch(event);
        } catch (Exception e) {
            // 한 건이 깨져도 구독은 계속돼야 한다. 여기서 예외가 새어나가면 리스너 컨테이너가
            // 해당 메시지를 재처리하거나 로그만 남기고 끝나, 원인을 찾기 어려워진다.
            log.warn("상담 이벤트 수신 처리 실패", e);
        }
    }
}
