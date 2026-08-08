package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.FairCancellationStatusResponse;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FairCancellationStatusServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 8, 7, 10, 0);

    @Mock
    private FairMapper fairMapper;

    @InjectMocks
    private FairCancellationStatusService cancellationStatusService;

    @Test
    @DisplayName("취소된 행사는 canceled=true와 canceledAt을 그대로 응답한다")
    void getCancellationStatus_취소된행사면_true를응답한다() {
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        fair.setCanceledAt(CANCELED_AT);
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        FairCancellationStatusResponse response = cancellationStatusService.getCancellationStatus(FAIR_ID);

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.canceled()).isTrue();
        assertThat(response.canceledAt()).isEqualTo(CANCELED_AT);
    }

    @Test
    @DisplayName("취소되지 않은 행사는 canceled=false와 canceledAt=null을 응답한다")
    void getCancellationStatus_취소안됐으면_false를응답한다() {
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        fair.setCanceledAt(null);
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        FairCancellationStatusResponse response = cancellationStatusService.getCancellationStatus(FAIR_ID);

        assertThat(response.canceled()).isFalse();
        assertThat(response.canceledAt()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 행사면 FAIR_NOT_FOUND를 던진다")
    void getCancellationStatus_행사없으면_예외를던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        assertThatThrownBy(() -> cancellationStatusService.getCancellationStatus(FAIR_ID))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.FAIR_NOT_FOUND);
    }

    @Test
    @DisplayName("유효하지 않은 fairId면 INVALID_INPUT_VALUE를 던진다")
    void getCancellationStatus_fairId유효하지않으면_예외를던진다() {
        assertThatThrownBy(() -> cancellationStatusService.getCancellationStatus(0L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
