package com.ms.petopia.api.chat.config;

import com.ms.petopia.api.chat.sse.ChatEventPublisher;
import com.ms.petopia.api.chat.sse.ChatEventSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 상담 이벤트 구독 설정.
 *
 * <p>Spring Boot는 {@code RedisMessageListenerContainer}를 자동 구성하지 않는다. 이 빈이 없으면
 * 발행은 성공하는데 아무도 받지 않아, <b>단일 인스턴스에서는 정상으로 보이고 스케일아웃하면
 * 일부 사용자에게만 이벤트가 안 가는</b> 형태로 드러난다.
 */
@Configuration
public class ChatStreamConfig {

    @Bean
    public RedisMessageListenerContainer chatRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ChatEventSubscriber chatEventSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatEventSubscriber, new ChannelTopic(ChatEventPublisher.CHANNEL));
        return container;
    }
}
