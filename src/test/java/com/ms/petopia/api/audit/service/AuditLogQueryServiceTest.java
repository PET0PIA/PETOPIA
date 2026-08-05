package com.ms.petopia.api.audit.service;

import com.ms.petopia.api.audit.dto.AuditLogListResponse;
import com.ms.petopia.api.audit.dto.AuditLogRow;
import com.ms.petopia.api.audit.mapper.AuditLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogQueryServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditLogQueryService auditLogQueryService;

    // ===== targetType + targetId =====

    @Test
    @DisplayName("targetType과 targetId가 있으면 selectByTarget을 호출하고 결과를 반환한다")
    void query_targetType과targetId가있으면_selectByTarget을_호출한다() {
        given(auditLogMapper.selectByTarget("FAIR", 3L, 0L, 20))
                .willReturn(List.of(new AuditLogRow()));

        AuditLogListResponse response =
                auditLogQueryService.query("FAIR", 3L, null, null, 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        verify(auditLogMapper).selectByTarget("FAIR", 3L, 0L, 20);
    }

    // ===== actorUserId =====

    @Test
    @DisplayName("actorUserId만 있으면 selectByActorUserId를 호출한다")
    void query_actorUserId만있으면_selectByActorUserId를_호출한다() {
        given(auditLogMapper.selectByActorUserId(7L, 0L, 20))
                .willReturn(List.of(new AuditLogRow()));

        AuditLogListResponse response =
                auditLogQueryService.query(null, null, 7L, null, 0, 20);

        assertThat(response.items()).hasSize(1);
        verify(auditLogMapper).selectByActorUserId(7L, 0L, 20);
    }

    // ===== actionType =====

    @Test
    @DisplayName("actionType만 있으면 selectByActionType을 호출한다")
    void query_actionType만있으면_selectByActionType을_호출한다() {
        given(auditLogMapper.selectByActionType("FAIR_APPROVE", 0L, 20))
                .willReturn(List.of(new AuditLogRow()));

        AuditLogListResponse response =
                auditLogQueryService.query(null, null, null, "FAIR_APPROVE", 0, 20);

        assertThat(response.items()).hasSize(1);
        verify(auditLogMapper).selectByActionType("FAIR_APPROVE", 0L, 20);
    }

    // ===== 필터 없음 =====

    @Test
    @DisplayName("필터가 하나도 없으면 mapper를 전혀 호출하지 않고 빈 리스트를 반환한다")
    void query_필터없으면_빈리스트를_반환한다() {
        AuditLogListResponse response =
                auditLogQueryService.query(null, null, null, null, 0, 20);

        assertThat(response.items()).isEmpty();
        // 어떤 mapper 메서드도 호출되지 않았는지 확인
        verify(auditLogMapper, never()).selectByTarget(any(), any(), anyLong(), anyInt());
        verify(auditLogMapper, never()).selectByActorUserId(any(), anyLong(), anyInt());
        verify(auditLogMapper, never()).selectByActionType(any(), anyLong(), anyInt());
    }

    // ===== 페이지네이션 offset 계산 =====

    @Test
    @DisplayName("page=2, size=10이면 offset=20으로 계산해서 mapper를 호출한다")
    void query_page와size로_offset을_올바르게_계산한다() {
        given(auditLogMapper.selectByActorUserId(7L, 20L, 10))
                .willReturn(List.of());

        auditLogQueryService.query(null, null, 7L, null, 2, 10);

        // offset = page * size = 2 * 10 = 20
        verify(auditLogMapper).selectByActorUserId(7L, 20L, 10);
    }
}
