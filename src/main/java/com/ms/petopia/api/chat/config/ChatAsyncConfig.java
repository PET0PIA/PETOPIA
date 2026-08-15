package com.ms.petopia.api.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AI 답변 전용 스레드 풀.
 *
 * <p>기본 executor를 함께 쓰지 않는 이유: Claude 호출은 수 초가 걸리는 외부 I/O다. 실시간
 * 통계 이벤트 같은 짧은 {@code @Async} 작업과 같은 풀에 두면, 문의가 몰릴 때 AI 호출이 풀을
 * 점유해 다른 비동기 작업이 밀린다.
 *
 * <p>큐를 짧게(50) 두는 것도 의도다. 큐가 길면 5분 전에 들어온 질문에 지금 답이 달리는데,
 * 그건 답이 없느니만 못하다.
 *
 * <p><b>거부 정책을 두지 않는다.</b> 기본값인 {@code AbortPolicy}가 그대로 예외를 던지고,
 * 제출한 쪽({@code ChatAiAnswerListener})이 그 예외를 잡아 선점해 둔 AI 한도를 되돌린다.
 * 여기서 조용히 버리면 사용자는 답변도 못 받고 한도만 잃으며, 그 사실이 로그에도 남지 않는다.
 */
@Configuration
public class ChatAsyncConfig {

    public static final String CHAT_AI_EXECUTOR = "chatAiExecutor";

    @Bean(CHAT_AI_EXECUTOR)
    public ThreadPoolTaskExecutor chatAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("chat-ai-");
        executor.initialize();
        return executor;
    }
}
