package com.ms.petopia.api.audit.controller;

import com.ms.petopia.api.audit.dto.AuditLogListResponse;
import com.ms.petopia.api.audit.service.AuditLogQueryService;
import com.ms.petopia.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock
    private AuditLogQueryService auditLogQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuditLogController(auditLogQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("targetType과 targetId로 조회하면 200 OK와 결과를 반환한다")
    void getAuditLogs_targetType과targetId로_조회하면_200_OK() throws Exception {
        given(auditLogQueryService.query("FAIR", 3L, null, null, 0, 20))
                .willReturn(new AuditLogListResponse(List.of(), 0, 20, 0L, false));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("X-User-Id", 99)
                        .param("targetType", "FAIR")
                        .param("targetId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(auditLogQueryService).query("FAIR", 3L, null, null, 0, 20);
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 400을 반환한다")
    void getAuditLogs_헤더없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("파라미터 없이 조회해도 200 OK를 반환하고 서비스에 null을 전달한다")
    void getAuditLogs_파라미터없어도_200_OK() throws Exception {
        given(auditLogQueryService.query(null, null, null, null, 0, 20))
                .willReturn(new AuditLogListResponse(List.of(), 0, 20, 0L, false));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("X-User-Id", 99))
                .andExpect(status().isOk());

        verify(auditLogQueryService).query(null, null, null, null, 0, 20);
    }

    @Test
    @DisplayName("actorUserId로 조회하면 서비스에 올바르게 전달한다")
    void getAuditLogs_actorUserId로_조회하면_서비스에_전달한다() throws Exception {
        given(auditLogQueryService.query(null, null, 7L, null, 0, 20))
                .willReturn(new AuditLogListResponse(List.of(), 0, 20, 0L, false));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("X-User-Id", 99)
                        .param("actorUserId", "7"))
                .andExpect(status().isOk());

        verify(auditLogQueryService).query(null, null, 7L, null, 0, 20);
    }
}
