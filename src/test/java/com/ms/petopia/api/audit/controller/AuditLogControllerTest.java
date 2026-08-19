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

import java.time.LocalDate;
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
        given(auditLogQueryService.query("FAIR", 3L, null, null, null, null, 0, 20))
                .willReturn(new AuditLogListResponse(List.of(), 0, 20, 0L, false));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("targetType", "FAIR")
                        .param("targetId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));

        verify(auditLogQueryService).query("FAIR", 3L, null, null, null, null, 0, 20);
    }

    @Test
    @DisplayName("필터 파라미터가 하나도 없어도 200 OK (전체 조회로 처리)")
    void getAuditLogs_파라미터없어도_200() throws Exception {
        given(auditLogQueryService.query(null, null, null, null, null, null, 0, 20))
                .willReturn(new AuditLogListResponse(List.of(), 0, 20, 0L, false));

        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("actorUserId로 조회하면 서비스에 올바르게 전달한다")
    void getAuditLogs_actorUserId로_조회하면_서비스에_전달한다() throws Exception {
        given(auditLogQueryService.query(null, null, 7L, null, null, null, 0, 20))
                .willReturn(new AuditLogListResponse(List.of(), 0, 20, 0L, false));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("actorUserId", "7"))
                .andExpect(status().isOk());

        verify(auditLogQueryService).query(null, null, 7L, null, null, null, 0, 20);
    }

    @Test
    @DisplayName("행위자·액션·기간을 함께 넘기면 전부 서비스로 전달한다 (복합 조회)")
    void getAuditLogs_복합조건을_전달하면_서비스로_다전달한다() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 19);
        given(auditLogQueryService.query(null, null, 7L, "FAIR_APPROVE", startDate, endDate, 0, 20))
                .willReturn(new AuditLogListResponse(List.of(), 0, 20, 0L, false));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("actorUserId", "7")
                        .param("actionType", "FAIR_APPROVE")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-19"))
                .andExpect(status().isOk());

        verify(auditLogQueryService).query(null, null, 7L, "FAIR_APPROVE", startDate, endDate, 0, 20);
    }
}
