package com.ms.petopia.api.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AI 답변 전용 스레드 풀.
 *
 * <p>기본 executor를 함께 쓰지 않는 이유: Claude 호출은 수 초가 걸리는 외부 I/O다. 실시간
 * 통계 이벤트 같은 짧은 {@code @Async} 작업과 같은 풀에 두면, 문의가 몰릴 때 AI 호출이 풀을
 * 점유해 다른 비동기 작업이 밀린다.
 *
 * <p>큐를 짧게(50) 두는 것도 의도다. 큐가 길면 5분 전에 들어온 질문에 지금 답이 달리는데,
 * 그건 답이 없느니만 못하다. 넘치면 거부되고, 그 문의는 그냥 상담사 대기열로 간다.
 */
@Configuration
public class ChatAsyncConfig {

    public static final String CHAT_AI_EXECUTOR = "chatAiExecutor";

    @Bean(CHAT_AI_EXECUTOR)
    public Executor chatAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("chat-ai-");
        // 큐가 가득 차면 호출 스레드에서 실행하지 않고 조용히 버린다. CallerRunsPolicy로 두면
        // 이벤트 발행 스레드가 Claude 호출에 붙잡혀 상담 흐름 전체가 느려진다.
        executor.setRejectedExecutionHandler((runnable, threadPoolExecutor) -> { });
        executor.initialize();
        return executor;
    }
}
