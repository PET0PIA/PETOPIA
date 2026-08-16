package com.ms.petopia.api.chat.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 이 인스턴스가 들고 있는 SSE 연결 목록.
 *
 * <p><b>이 레지스트리는 JVM 메모리에만 존재한다.</b> ECS 태스크가 여러 개면 상담사의 요청이
 * 고객의 연결을 들고 있지 않은 태스크에 도달할 수 있으므로, 이벤트는 반드시
 * {@link ChatEventPublisher}(Redis Pub/Sub)를 거쳐 모든 인스턴스로 퍼진 뒤 각자의 레지스트리로
 * 전달돼야 한다. 여기에 직접 넣으면 로컬에서는 되고 운영에서만 조용히 실패한다.
 */
@Slf4j
@Component
public class ChatEmitterRegistry {

    /**
     * 연결 수명. 브라우저가 만료 후 다시 붙으므로 짧아도 되지만, 너무 짧으면 재연결이 잦아
     * 티켓 발급 요청이 늘어난다. 30분은 그 사이의 타협점이다.
     */
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(Long conversationId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.computeIfAbsent(conversationId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> remove(conversationId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    /** 이 인스턴스에 붙어 있는 연결에만 보낸다. 다른 인스턴스 몫은 구독자가 각자 처리한다. */
    public void dispatch(ChatStreamEvent event) {
        List<SseEmitter> targets = emitters.get(event.conversationId());
        if (targets == null || targets.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : targets) {
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .name(event.type())
                    .data(event.payload());
            if (event.eventId() != null) {
                builder.id(String.valueOf(event.eventId()));
            }
            send(event.conversationId(), emitter, builder);
        }
    }

    /**
     * 유휴 연결 유지용 주석 프레임.
     *
     * <p>프록시(nginx/ALB)는 일정 시간 아무것도 흐르지 않는 연결을 끊는다. 상담은 몇 분씩
     * 조용한 게 정상이라, 주기적으로 뭔가를 보내지 않으면 "조용해서 끊기고 → 재연결"이 반복된다.
     */
    @Scheduled(fixedDelay = 15_000L)
    public void ping() {
        emitters.forEach((conversationId, list) -> {
            for (SseEmitter emitter : list) {
                send(conversationId, emitter, SseEmitter.event().comment("ping"));
            }
        });
    }

    private void send(Long conversationId, SseEmitter emitter, SseEmitter.SseEventBuilder builder) {
        try {
            emitter.send(builder);
        } catch (IOException | IllegalStateException e) {
            // 이미 끊긴 연결에 쓰면 예외가 난다. 정상적인 상황이므로 로그를 남기지 않고
            // 목록에서만 정리한다 - 사용자가 탭을 닫을 때마다 에러 로그가 쌓이면 안 된다.
            remove(conversationId, emitter);
            emitter.completeWithError(e);
        }
    }

    private void remove(Long conversationId, SseEmitter emitter) {
        emitters.computeIfPresent(conversationId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
