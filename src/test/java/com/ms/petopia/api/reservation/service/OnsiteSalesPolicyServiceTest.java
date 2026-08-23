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
        // 정원을 설정한 적 없는 운영일은 "제한 없음"이다 - 기존 행사가 갑자기 매진되면 안 된다.
        assertThat(response.capacity()).isNull();
        assertThat(response.reservedCount()).isZero();
    }

    @Test
    void createsDatePolicyWithClosedByExplicitRequest() {
        FairDateSnapshot fairDate = fairDate();
        OnsiteSalesPolicyRow saved = policy(0, "CLOSED", 0);
        given(mapper.selectFairDateForUpdate(10L, 20L)).willReturn(fairDate);
        given(timeProvider.now()).willReturn(NOW);
        given(mapper.selectPolicy(20L)).willReturn(null, saved);

        OnsiteSalesPolicyResponse response = service.save(
                10L, 20L, 30L, new UpdateOnsiteSalesPolicyRequest(0L, null, "CLOSED", null));

        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(response.version()).isZero();
        verify(mapper).insertPolicy(20L, 0, null, "CLOSED", 30L, NOW);
    }

    @Test
    void rejectsStaleAdminUpdate() {
        given(mapper.selectFairDateForUpdate(10L, 20L)).willReturn(fairDate());
        given(timeProvider.now()).willReturn(NOW);
        given(mapper.selectPolicy(20L)).willReturn(policy(10_000, "OPEN", 2));
        given(mapper.updatePolicy(20L, 12_000, null, "PAUSED", 30L, 1, NOW)).willReturn(0);

        assertThatThrownBy(() -> service.save(
                10L, 20L, 30L, new UpdateOnsiteSalesPolicyRequest(12_000L, null, "PAUSED", 1)))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ONSITE_SALES_POLICY_CONFLICT);
    }

    @Test
    void savesOnsiteCapacityAndKeepsSoldCount() {
        OnsiteSalesPolicyRow current = policy(10_000, "OPEN", 1);
        OnsiteSalesPolicyRow saved = policy(10_000, "OPEN", 2);
        saved.setCapacity(50);
        saved.setReservedCount(7);
        given(mapper.selectFairDateForUpdate(10L, 20L)).willReturn(fairDate());
        given(timeProvider.now()).willReturn(NOW);
        given(mapper.selectPolicy(20L)).willReturn(current, saved);
        given(mapper.updatePolicy(20L, 10_000, 50, "OPEN", 30L, 1, NOW)).willReturn(1);

        OnsiteSalesPolicyResponse response = service.save(
                10L, 20L, 30L, new UpdateOnsiteSalesPolicyRequest(10_000L, 50, "OPEN", 1));

        assertThat(response.capacity()).isEqualTo(50);
        // 판매된 수는 관리자 저장이 건드리지 않는다 - 예매·취소만 움직이는 값이다.
        assertThat(response.reservedCount()).isEqualTo(7);
    }

    @Test
    void rejectsNegativeOnsiteCapacity() {
        assertThatThrownBy(() -> service.save(
                10L, 20L, 30L, new UpdateOnsiteSalesPolicyRequest(10_000L, -1, "OPEN", 1)))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
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
