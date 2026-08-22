package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.config.ChatAsyncConfig;
import com.ms.petopia.api.chat.event.ChatAiAnswerRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AI 답변 요청을 받아 전용 스레드 풀로 넘긴다.
 *
 * <p><b>{@code @Async}를 쓰지 않고 직접 제출하는 이유</b>: {@code @Async}는 큐가 넘쳤을 때
 * 거부를 호출부에 알려주지 않는다. 그런데 이 시점에는 이미 대화의 진행 중 호출을 선점해 둔
 * 상태라, 작업이 실행되지 않으면 그 선점을 되돌릴 주체가 사라진다 - 그 대화는 stale 창이
 * 지날 때까지 자동 응대가 막히고, 사용자는 답변도 안내도 받지 못한다. 직접 제출하면
 * {@code RejectedExecutionException}을 잡아 되돌리고 이유를 남길 수 있다.
 *
 * <p>{@code @TransactionalEventListener}라서 사용자 메시지가 커밋된 뒤 실행된다. 제출만
 * 하고 끝나므로 요청 스레드를 붙잡지 않는다(실제 Claude 호출은 풀에서 돈다).
 */
@Slf4j
@Component
public class ChatAiAnswerListener {

    private final ThreadPoolTaskExecutor executor;
    private final ChatAiAnswerService answerService;

    public ChatAiAnswerListener(@Qualifier(ChatAsyncConfig.CHAT_AI_EXECUTOR) ThreadPoolTaskExecutor executor,
                                ChatAiAnswerService answerService) {
        this.executor = executor;
        this.answerService = answerService;
    }

    @TransactionalEventListener
    public void onAiAnswerRequested(ChatAiAnswerRequestedEvent event) {
        try {
            executor.execute(() -> answerWithRelease(event));
        } catch (RuntimeException e) {
            // 큐가 가득 찼다(또는 종료 중이다). 이 경로는 tryAnswer에 들어가지도 못하므로
            // 화면에 아무것도 남지 않는다 - 그래서 이관 안내를 여기서 붙인다. 상태는
            // WAITING_AGENT로 남아 대기열에 들어가므로 상담 자체는 이어진다.
            log.error("AI 답변 작업을 큐에 넣지 못했다. 이관 안내로 넘긴다. conversationId={}, 큐={}/{}",
                    event.conversationId(),
                    executor.getThreadPoolExecutor().getQueue().size(),
                    executor.getQueueCapacity(), e);
            answerService.escalateWithoutAnswer(event.conversationId());
            answerService.releaseCall(event.conversationId());
        }
    }

    /**
     * 답변을 시도하고, 끝나면 선점을 되돌린다.
     *
     * <p>이 조립을 {@link ChatAiAnswerService} 안에서 하지 않는 이유: 같은 빈 안에서 this로
     * 호출하면 프록시를 거치지 않아 {@code @Transactional}이 무시된다. 그러면 답변 트랜잭션이
     * 롤백될 때 반납까지 함께 취소돼, 정작 반납이 필요한 실패 경로에서 동작하지 않는다.
     *
     * <p><b>반납은 성공에도 필요하다.</b> 선점은 "지금 이 대화에 호출이 돌고 있다"는 표시일
     * 뿐이므로, 답변을 남겼으면 그 표시를 지워야 다음 질문에 답할 수 있다. 성공 경로에서
     * 반납을 빠뜨리면 stale 창(3분) 동안 그 대화의 후속 질문이 조용히 무응답이 된다.
     *
     * <p>{@code ClaudeSupportResponder}는 Anthropic 예외만 잡으므로 구조화 출력 역직렬화 같은
     * 예상 못한 예외는 여기까지 올라온다. 그것까지 잡아야 선점이 stale 창까지 남지 않는다.
     */
    private void answerWithRelease(ChatAiAnswerRequestedEvent event) {
        try {
            answerService.tryAnswer(event);
        } catch (RuntimeException e) {
            log.error("AI 답변 처리 중 예상 못한 오류. conversationId={}", event.conversationId(), e);
        } finally {
            answerService.releaseCall(event.conversationId());
        }
    }
}
