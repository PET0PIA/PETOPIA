package com.ms.petopia.api.audit.service;

import com.ms.petopia.api.audit.dto.AuditLogListResponse;
import com.ms.petopia.api.audit.dto.AuditLogRow;
import com.ms.petopia.api.audit.dto.AuditLogSearchCondition;
import com.ms.petopia.api.audit.mapper.AuditLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogQueryServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditLogQueryService auditLogQueryService;

    @Test
    @DisplayName("targetType과 targetId가 있으면 그 조건으로 search/countSearch를 호출한다")
    void query_targetType과targetId가있으면_조건에담아_search를_호출한다() {
        AuditLogSearchCondition expected = AuditLogSearchCondition.builder()
                .targetType("FAIR").targetId(3L).offset(0L).limit(20).build();
        given(auditLogMapper.search(expected)).willReturn(List.of(new AuditLogRow()));
        given(auditLogMapper.countSearch(expected)).willReturn(1L);

        AuditLogListResponse response =
                auditLogQueryService.query("FAIR", 3L, null, null, null, null, 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.hasNext()).isFalse();
        verify(auditLogMapper).search(expected);
        verify(auditLogMapper).countSearch(expected);
    }

    @Test
    @DisplayName("행위자·액션·기간을 동시에 지정하면 한 조건 객체로 묶여서 넘어간다 (복합 조회)")
    void query_여러조건을_동시에주면_하나의_condition으로_조합된다() {
        LocalDateTime expectedStartAt = LocalDate.of(2026, 8, 1).atStartOfDay();
        LocalDateTime expectedEndAt = LocalDate.of(2026, 8, 20).atStartOfDay(); // endDate(8/19) + 1일

        AuditLogSearchCondition expected = AuditLogSearchCondition.builder()
                .actorUserId(7L)
                .actionType("FAIR_APPROVE")
                .startAt(expectedStartAt)
                .endAt(expectedEndAt)
                .offset(0L).limit(20)
                .build();
        given(auditLogMapper.search(expected)).willReturn(List.of(new AuditLogRow()));
        given(auditLogMapper.countSearch(expected)).willReturn(1L);

        AuditLogListResponse response = auditLogQueryService.query(
                null, null, 7L, "FAIR_APPROVE",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 19),
                0, 20);

        assertThat(response.items()).hasSize(1);
        verify(auditLogMapper).search(expected);
    }

    @Test
    @DisplayName("필터가 하나도 없어도 예외 없이 조건 없는 전체 조회로 처리한다")
    void query_필터가없어도_전체조회로_처리한다() {
        AuditLogSearchCondition expected = AuditLogSearchCondition.builder()
                .offset(0L).limit(20).build();
        given(auditLogMapper.search(expected)).willReturn(List.of());
        given(auditLogMapper.countSearch(expected)).willReturn(0L);

        AuditLogListResponse response =
                auditLogQueryService.query(null, null, null, null, null, null, 0, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 CommonException(INVALID_INPUT_VALUE)을 던진다")
    void query_시작일이종료일보다늦으면_예외를_던진다() {
        assertThatThrownBy(() -> auditLogQueryService.query(
                null, null, null, null,
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 1),
                0, 20))
                .isInstanceOf(CommonException.class)
                .satisfies(ex -> assertThat(((CommonException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    @DisplayName("targetType이 공백이면 null로 정규화되어 target 조건 없이 조회한다")
    void query_targetType이공백이면_null로정규화된다() {
        AuditLogSearchCondition expected = AuditLogSearchCondition.builder()
                .targetId(3L).offset(0L).limit(20).build();
        given(auditLogMapper.search(expected)).willReturn(List.of());
        given(auditLogMapper.countSearch(expected)).willReturn(0L);

        auditLogQueryService.query("  ", 3L, null, null, null, null, 0, 20);

        verify(auditLogMapper).search(expected);
    }

    @Test
    @DisplayName("page=2, size=10이면 offset=20으로 계산해서 mapper를 호출한다")
    void query_page와size로_offset을_올바르게_계산한다() {
        AuditLogSearchCondition expected = AuditLogSearchCondition.builder()
                .actorUserId(7L).offset(20L).limit(10).build();
        given(auditLogMapper.search(expected)).willReturn(List.of());
        given(auditLogMapper.countSearch(expected)).willReturn(15L);

        AuditLogListResponse response = auditLogQueryService.query(null, null, 7L, null, null, null, 2, 10);

        verify(auditLogMapper).search(expected);
        // 전체 15건, offset 20 → 더 이상 데이터 없음
        assertThat(response.hasNext()).isFalse();
    }
}
