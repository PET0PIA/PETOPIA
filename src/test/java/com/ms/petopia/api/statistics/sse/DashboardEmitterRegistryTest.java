package com.ms.petopia.api.statistics.sse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardEmitterRegistryTest {

    private DashboardEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DashboardEmitterRegistry();
    }

    @Test
    @DisplayName("register - emitter가 목록에 추가되고 반환됨")
    void register_addsEmitterToList() {
        SseEmitter emitter = registry.register(1L);

        assertThat(registry.getEmitters(1L)).containsExactly(emitter);
    }

    @Test
    @DisplayName("register - 같은 fairId에 여러 클라이언트 모두 추가됨")
    void register_multipleClients_allTracked() {
        SseEmitter e1 = registry.register(1L);
        SseEmitter e2 = registry.register(1L);

        assertThat(registry.getEmitters(1L)).containsExactlyInAnyOrder(e1, e2);
    }

    @Test
    @DisplayName("register - 다른 fairId끼리 목록이 분리됨")
    void register_differentFairIds_separateLists() {
        SseEmitter e1 = registry.register(1L);
        SseEmitter e2 = registry.register(2L);

        assertThat(registry.getEmitters(1L)).containsExactly(e1);
        assertThat(registry.getEmitters(2L)).containsExactly(e2);
    }

    @Test
    @DisplayName("getEmitters - 등록 없는 fairId는 빈 리스트")
    void getEmitters_unknownFairId_returnsEmpty() {
        assertThat(registry.getEmitters(999L)).isEmpty();
    }
}
