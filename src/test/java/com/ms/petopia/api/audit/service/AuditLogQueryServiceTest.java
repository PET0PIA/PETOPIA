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

import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        given(auditLogMapper.countByTarget("FAIR", 3L)).willReturn(1L);

        AuditLogListResponse response =
                auditLogQueryService.query("FAIR", 3L, null, null, 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.hasNext()).isFalse();
        verify(auditLogMapper).selectByTarget("FAIR", 3L, 0L, 20);
        verify(auditLogMapper).countByTarget("FAIR", 3L);
    }

    @Test
    @DisplayName("전체 건수가 size보다 많으면 hasNext가 true다")
    void query_totalElements가size보다크면_hasNext가_true다() {
        List<AuditLogRow> page = List.of(new AuditLogRow(), new AuditLogRow());
        given(auditLogMapper.selectByTarget("FAIR", 1L, 0L, 2)).willReturn(page);
        given(auditLogMapper.countByTarget("FAIR", 1L)).willReturn(5L);

        AuditLogListResponse response =
                auditLogQueryService.query("FAIR", 1L, null, null, 0, 2);

        assertThat(response.hasNext()).isTrue();
        assertThat(response.totalElements()).isEqualTo(5L);
    }

    // ===== actorUserId =====

    @Test
    @DisplayName("actorUserId만 있으면 selectByActorUserId를 호출한다")
    void query_actorUserId만있으면_selectByActorUserId를_호출한다() {
        given(auditLogMapper.selectByActorUserId(7L, 0L, 20))
                .willReturn(List.of(new AuditLogRow()));
        given(auditLogMapper.countByActorUserId(7L)).willReturn(1L);

        AuditLogListResponse response =
                auditLogQueryService.query(null, null, 7L, null, 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1L);
        verify(auditLogMapper).selectByActorUserId(7L, 0L, 20);
        verify(auditLogMapper).countByActorUserId(7L);
    }

    // ===== actionType =====

    @Test
    @DisplayName("actionType만 있으면 selectByActionType을 호출한다")
    void query_actionType만있으면_selectByActionType을_호출한다() {
        given(auditLogMapper.selectByActionType("FAIR_APPROVE", 0L, 20))
                .willReturn(List.of(new AuditLogRow()));
        given(auditLogMapper.countByActionType("FAIR_APPROVE")).willReturn(1L);

        AuditLogListResponse response =
                auditLogQueryService.query(null, null, null, "FAIR_APPROVE", 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1L);
        verify(auditLogMapper).selectByActionType("FAIR_APPROVE", 0L, 20);
        verify(auditLogMapper).countByActionType("FAIR_APPROVE");
    }

    // ===== 필터 없음 =====

    @Test
    @DisplayName("필터가 하나도 없으면 CommonException(INVALID_INPUT_VALUE)을 던진다")
    void query_필터없으면_CommonException을_던진다() {
        assertThatThrownBy(() -> auditLogQueryService.query(null, null, null, null, 0, 20))
                .isInstanceOf(CommonException.class)
                .satisfies(ex -> assertThat(((CommonException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));

        verify(auditLogMapper, never()).selectByTarget(any(), any(), anyLong(), anyInt());
        verify(auditLogMapper, never()).selectByActorUserId(any(), anyLong(), anyInt());
        verify(auditLogMapper, never()).selectByActionType(any(), anyLong(), anyInt());
        verify(auditLogMapper, never()).countByTarget(any(), any());
        verify(auditLogMapper, never()).countByActorUserId(any());
        verify(auditLogMapper, never()).countByActionType(any());
    }

    // ===== 페이지네이션 offset 계산 =====

    @Test
    @DisplayName("page=2, size=10이면 offset=20으로 계산해서 mapper를 호출한다")
    void query_page와size로_offset을_올바르게_계산한다() {
        given(auditLogMapper.selectByActorUserId(7L, 20L, 10)).willReturn(List.of());
        given(auditLogMapper.countByActorUserId(7L)).willReturn(15L);

        AuditLogListResponse response = auditLogQueryService.query(null, null, 7L, null, 2, 10);

        // offset = page * size = 2 * 10 = 20
        verify(auditLogMapper).selectByActorUserId(7L, 20L, 10);
        // 전체 15건, offset 20 → 더 이상 데이터 없음
        assertThat(response.hasNext()).isFalse();
    }
}
