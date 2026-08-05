package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.FairDateSnapshot;
import com.ms.petopia.api.reservation.dto.OnsiteSalesPolicyResponse;
import com.ms.petopia.api.reservation.dto.OnsiteSalesPolicyRow;
import com.ms.petopia.api.reservation.dto.UpdateOnsiteSalesPolicyRequest;
import com.ms.petopia.api.reservation.mapper.OnsiteReservationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OnsiteSalesPolicyServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 9, 0);

    @Mock
    private OnsiteReservationMapper mapper;
    @Mock
    private ReservationOperatorAccessService accessService;
    @Mock
    private ReservationTimeProvider timeProvider;
    @InjectMocks
    private OnsiteSalesPolicyService service;

    @Test
    void returnsConfiguredDatePolicyForAuthorizedAdmin() {
        given(mapper.selectFairDate(10L, 20L)).willReturn(fairDate());
        given(mapper.selectPolicy(20L)).willReturn(policy(12_000, "OPEN", 3));

        OnsiteSalesPolicyResponse response = service.get(10L, 20L, 30L);

        assertThat(response.operationDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(response.price()).isEqualTo(12_000);
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.version()).isEqualTo(3);
    }

    @Test
    void returnsClosedDefaultsWhenPolicyIsNotConfigured() {
        given(mapper.selectFairDate(10L, 20L)).willReturn(fairDate());
        given(mapper.selectPolicy(20L)).willReturn(null);

        OnsiteSalesPolicyResponse response = service.get(10L, 20L, 30L);

        assertThat(response.price()).isZero();
        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(response.version()).isZero();
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    void createsDatePolicyWithClosedByExplicitRequest() {
        FairDateSnapshot fairDate = fairDate();
        OnsiteSalesPolicyRow saved = policy(0, "CLOSED", 0);
        given(mapper.selectFairDateForUpdate(10L, 20L)).willReturn(fairDate);
        given(timeProvider.now()).willReturn(NOW);
        given(mapper.selectPolicy(20L)).willReturn(null, saved);

        OnsiteSalesPolicyResponse response = service.save(
                10L, 20L, 30L, new UpdateOnsiteSalesPolicyRequest(0L, "CLOSED", null));

        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(response.version()).isZero();
        verify(mapper).insertPolicy(20L, 0, "CLOSED", 30L, NOW);
    }

    @Test
    void rejectsStaleAdminUpdate() {
        given(mapper.selectFairDateForUpdate(10L, 20L)).willReturn(fairDate());
        given(timeProvider.now()).willReturn(NOW);
        given(mapper.selectPolicy(20L)).willReturn(policy(10_000, "OPEN", 2));
        given(mapper.updatePolicy(20L, 12_000, "PAUSED", 30L, 1, NOW)).willReturn(0);

        assertThatThrownBy(() -> service.save(
                10L, 20L, 30L, new UpdateOnsiteSalesPolicyRequest(12_000L, "PAUSED", 1)))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ONSITE_SALES_POLICY_CONFLICT);
    }

    private FairDateSnapshot fairDate() {
        FairDateSnapshot row = new FairDateSnapshot();
        row.setFairDateId(20L);
        row.setFairId(10L);
        row.setOperationDate(LocalDate.of(2026, 8, 1));
        return row;
    }

    private OnsiteSalesPolicyRow policy(long price, String status, int version) {
        OnsiteSalesPolicyRow row = new OnsiteSalesPolicyRow();
        row.setFairDateId(20L);
        row.setPrice(price);
        row.setStatus(status);
        row.setVersion(version);
        row.setUpdatedAt(NOW);
        return row;
    }
}
