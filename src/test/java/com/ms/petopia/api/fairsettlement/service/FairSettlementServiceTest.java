package com.ms.petopia.api.fairsettlement.service;

import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.auth.service.MailService;
import com.ms.petopia.api.commisionrate.service.CommissionRateService;
import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.fairsettlement.dto.FairSettlementResponse;
import com.ms.petopia.api.fairsettlement.dto.FairSettlementRow;
import com.ms.petopia.api.fairsettlement.mapper.FairSettlementMapper;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.api.refund.mapper.RefundMapper;
import com.ms.petopia.api.settlement.client.FairContractClient;
import com.ms.petopia.api.settlement.dto.FairCancellationStatus;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * getByFairId 전용 테스트(2026-08-24) - "존재하지 않는 행사ID"와 "행사는 있지만 정산 미계산"을
 * 구분하는 로직만 검증한다. calculate/confirm/recalculate/reopen은 이 서비스 최초 작성 당시부터
 * 테스트가 없었고 이번 변경 범위 밖이라 손대지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class FairSettlementServiceTest {

    @Mock
    private FairSettlementMapper fairSettlementMapper;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private RefundMapper refundMapper;
    @Mock
    private CommissionRateService commissionRateService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RecruitNoticeMapper recruitNoticeMapper;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private FairContractClient fairContractClient;
    @Mock
    private FairAdminAccessGuard fairAdminAccessGuard;
    @Mock
    private AuthMapper authMapper;
    @Mock
    private MailService mailService;

    @InjectMocks
    private FairSettlementService fairSettlementService;

    private static final Long FAIR_ID = 10L;

    @Nested
    @DisplayName("getByFairId")
    class GetByFairId {

        @Test
        @DisplayName("존재하지 않는 행사 ID면 FAIR_NOT_FOUND를 던진다")
        void throwsFairNotFound_whenFairDoesNotExist() {
            willThrow(new CommonException(ErrorCode.FAIR_NOT_FOUND,
                    new RestClientResponseException("404", 404, "Not Found", null, null, null)))
                    .given(fairContractClient).getCancellationStatus(FAIR_ID);

            assertThatThrownBy(() -> fairSettlementService.getByFairId(FAIR_ID))
                    .isInstanceOf(CommonException.class)
                    .extracting(e -> ((CommonException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FAIR_NOT_FOUND);

            // 존재 확인에서 이미 실패했으니 정산 테이블은 조회하지 않아야 한다.
            verify(fairSettlementMapper, never()).selectByFairId(FAIR_ID);
        }

        @Test
        @DisplayName("행사는 있지만 계산된 정산이 없으면 null을 반환한다(에러 아님)")
        void returnsNull_whenFairExistsWithoutSettlement() {
            given(fairContractClient.getCancellationStatus(FAIR_ID))
                    .willReturn(new FairCancellationStatus(FAIR_ID, false, null));
            given(fairSettlementMapper.selectByFairId(FAIR_ID)).willReturn(null);

            FairSettlementResponse result = fairSettlementService.getByFairId(FAIR_ID);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("계산된 정산이 있으면 그대로 반환한다")
        void returnsSettlement_whenAlreadyCalculated() {
            given(fairContractClient.getCancellationStatus(FAIR_ID))
                    .willReturn(new FairCancellationStatus(FAIR_ID, false, null));
            FairSettlementRow row = new FairSettlementRow();
            row.setFairSettlementId(1L);
            row.setFairId(FAIR_ID);
            row.setStatus("PENDING");
            given(fairSettlementMapper.selectByFairId(FAIR_ID)).willReturn(row);

            FairSettlementResponse result = fairSettlementService.getByFairId(FAIR_ID);

            assertThat(result).isNotNull();
            assertThat(result.fairId()).isEqualTo(FAIR_ID);
            assertThat(result.status()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("담당 관리자가 아니면 행사 존재 확인 전에 ACCESS_DENIED를 던진다")
        void throwsAccessDenied_beforeCheckingExistence() {
            willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                    .given(fairAdminAccessGuard).checkAssigned(FAIR_ID);

            assertThatThrownBy(() -> fairSettlementService.getByFairId(FAIR_ID))
                    .isInstanceOf(CommonException.class)
                    .extracting(e -> ((CommonException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_DENIED);

            // 미배정 사용자에게 행사 존재 여부가 새어나가지 않도록, 접근 검증에서 막히면
            // 존재 확인 자체를 호출하지 않아야 한다(FairService.getOpeningFeeSummary와 동일 원칙).
            verify(fairContractClient, never()).getCancellationStatus(FAIR_ID);
        }
    }
}
