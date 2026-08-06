package com.ms.petopia.api.statistics.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DashboardEmitterRegistry {
    // ConcurrentHashMap : 여러 스레드가 동시에 읽고 써도 안전
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    public SseEmitter register(Long fairId){
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.computeIfAbsent(fairId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        // 연결이 끊기는 세가지 경우(정상종료, 타임아웃, 오류) 목록에서 제거
        Runnable cleanup = () -> remove(fairId, emitter); // Map.remove가 아닌 private remove() 호출 - List에서 개별 emitter 제거
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    // 특정 fair에 연결된 클라이언트 목록 반환
    public List<SseEmitter> getEmitters(Long fairId){
        return emitters.getOrDefault(fairId, List.of());
    }

    private void remove(Long fairId, SseEmitter emitter){
        List<SseEmitter> list = emitters.get(fairId);
        if(list != null){
            list.remove(emitter);
        };
    }
}
