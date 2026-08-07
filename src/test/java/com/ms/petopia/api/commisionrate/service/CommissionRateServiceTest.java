package com.ms.petopia.api.commisionrate.service;

import com.ms.petopia.api.commisionrate.dto.CommissionRateResponse;
import com.ms.petopia.api.commisionrate.dto.CommissionRateRow;
import com.ms.petopia.api.commisionrate.dto.CommissionRateScope;
import com.ms.petopia.api.commisionrate.mapper.CommissionRateMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommissionRateServiceTest {

    @Mock
    private CommissionRateMapper commissionRateMapper;

    @InjectMocks
    private CommissionRateService commissionRateService;

    private CommissionRateRow row(String scope, Long fairId, String rate) {
        CommissionRateRow row = new CommissionRateRow();
        row.setCommissionRateId(1L);
        row.setScope(scope);
        row.setFairId(fairId);
        row.setRate(new BigDecimal(rate));
        row.setUpdatedByUserId(99L);
        row.setUpdatedAt(LocalDateTime.now());
        return row;
    }

    @Test
    @DisplayName("행사별 override가 있으면 전역값보다 그걸 우선 적용한다")
    void resolveEffectiveRate_행사별override_우선적용() {
        given(commissionRateMapper.selectLatestByFair(10L)).willReturn(row("FAIR", 10L, "0.0300"));

        BigDecimal result = commissionRateService.resolveEffectiveRate(10L);

        assertThat(result).isEqualByComparingTo("0.0300");
        // override를 찾았으면 전역값까지 조회할 필요 없다
        verify(commissionRateMapper, never()).selectLatestGlobal();
    }

    @Test
    @DisplayName("행사별 override가 없으면 전역 기본값으로 떨어진다")
    void resolveEffectiveRate_override없음_전역값사용() {
        given(commissionRateMapper.selectLatestByFair(10L)).willReturn(null);
        given(commissionRateMapper.selectLatestGlobal()).willReturn(row("GLOBAL", null, "0.0700"));

        BigDecimal result = commissionRateService.resolveEffectiveRate(10L);

        assertThat(result).isEqualByComparingTo("0.0700");
    }

    @Test
    @DisplayName("전역값도 한 번도 설정된 적 없으면 부트스트랩 기본값(0.05)을 쓴다")
    void resolveEffectiveRate_아무것도없음_부트스트랩기본값() {
        given(commissionRateMapper.selectLatestByFair(10L)).willReturn(null);
        given(commissionRateMapper.selectLatestGlobal()).willReturn(null);

        BigDecimal result = commissionRateService.resolveEffectiveRate(10L);

        assertThat(result).isEqualByComparingTo("0.0500");
    }

    @Test
    @DisplayName("fairId 없이 조회하면 행사별 override는 보지 않고 전역값만 본다")
    void resolveEffectiveRate_fairId없음_전역값만조회() {
        given(commissionRateMapper.selectLatestGlobal()).willReturn(row("GLOBAL", null, "0.0700"));

        BigDecimal result = commissionRateService.resolveEffectiveRate(null);

        assertThat(result).isEqualByComparingTo("0.0700");
        verify(commissionRateMapper, never()).selectLatestByFair(any());
    }

    @Test
    @DisplayName("설정된 적 없는 요율을 조회하면 부트스트랩 기본값을 응답하고 updatedAt은 null이다")
    void getEffectiveRate_설정된적없음_부트스트랩기본값응답() {
        given(commissionRateMapper.selectLatestByFair(10L)).willReturn(null);
        given(commissionRateMapper.selectLatestGlobal()).willReturn(null);

        CommissionRateResponse result = commissionRateService.getEffectiveRate(10L);

        assertThat(result.rate()).isEqualByComparingTo("0.0500");
        assertThat(result.scope()).isEqualTo("GLOBAL");
        assertThat(result.fairId()).isNull();
        assertThat(result.updatedAt()).isNull();
    }

    @Test
    @DisplayName("GLOBAL 요율을 설정하면 fairId 없이 새 행이 저장된다")
    void setRate_GLOBAL_성공() {
        CommissionRateResponse result = commissionRateService.setRate(
                CommissionRateScope.GLOBAL, null, new BigDecimal("0.0600"), 99L);

        assertThat(result.scope()).isEqualTo("GLOBAL");
        assertThat(result.fairId()).isNull();
        assertThat(result.rate()).isEqualByComparingTo("0.0600");
        assertThat(result.updatedByUserId()).isEqualTo(99L);

        verify(commissionRateMapper).insert(any(CommissionRateRow.class));
    }

    @Test
    @DisplayName("FAIR 요율을 설정하면 그 fairId로 새 행이 저장된다")
    void setRate_FAIR_성공() {
        CommissionRateResponse result = commissionRateService.setRate(
                CommissionRateScope.FAIR, 10L, new BigDecimal("0.0300"), 99L);

        assertThat(result.scope()).isEqualTo("FAIR");
        assertThat(result.fairId()).isEqualTo(10L);
        assertThat(result.rate()).isEqualByComparingTo("0.0300");
    }

    @Test
    @DisplayName("GLOBAL인데 fairId를 같이 보내면 거부한다")
    void setRate_GLOBAL인데fairId있음_예외를던진다() {
        assertThatThrownBy(() -> commissionRateService.setRate(
                CommissionRateScope.GLOBAL, 10L, new BigDecimal("0.0500"), 99L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMISSION_RATE_INVALID_SCOPE);

        verify(commissionRateMapper, never()).insert(any(CommissionRateRow.class));
    }

    @Test
    @DisplayName("FAIR인데 fairId가 없으면 거부한다")
    void setRate_FAIR인데fairId없음_예외를던진다() {
        assertThatThrownBy(() -> commissionRateService.setRate(
                CommissionRateScope.FAIR, null, new BigDecimal("0.0500"), 99L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMISSION_RATE_INVALID_SCOPE);

        verify(commissionRateMapper, never()).insert(any(CommissionRateRow.class));
    }
}
