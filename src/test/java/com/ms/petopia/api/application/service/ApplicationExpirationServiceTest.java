package com.ms.petopia.api.application.service;

import com.ms.petopia.api.application.mapper.ApplicationExpirationMapper;
import com.ms.petopia.api.application.mapper.ApplicationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/*
 * ApplicationExpirationService 단위 테스트.
 * now는 서비스 내부에서 LocalDateTime.now()로 직접 생성하므로(별도 TimeProvider 없음)
 * 정확한 값을 스텁할 수 없어 any(LocalDateTime.class)로 매칭한다.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationExpirationServiceTest {

    @Mock
    private ApplicationExpirationMapper applicationExpirationMapper;

    @Mock
    private ApplicationMapper applicationMapper;

    @InjectMocks
    private ApplicationExpirationService applicationExpirationService;

    @Test
    @DisplayName("결제 기한 지난 신청서를 정상적으로 자동 취소한다")
    void expiresDueApplicationsSuccessfully() {

        // given: 대상 신청서 2건이 조회되고, 둘 다 정상적으로 UPDATE되는 상황
        given(applicationExpirationMapper.selectDueApplicationsForUpdate(any(LocalDateTime.class), eq(200)))
                .willReturn(List.of(1L, 2L));
        given(applicationExpirationMapper.expirePaymentPendingApplication(eq(1L), any(LocalDateTime.class)))
                .willReturn(1);
        given(applicationExpirationMapper.expirePaymentPendingApplication(eq(2L), any(LocalDateTime.class)))
                .willReturn(1);

        // when
        int expired = applicationExpirationService.expireDueApplications(200);

        // then: 2건 다 취소 처리된 걸로 카운트돼야 함
        assertThat(expired).isEqualTo(2);

    }

    @Test
    @DisplayName("자동 취소된 신청서에 딸린 REQUESTED 취소 요청도 함께 종료 처리한다")
    void closesDanglingCancelRequestWhenExpired() {

        // given: 결제기한 지나 자동취소 대상인 신청서에, 처리 대기 중인 취소 요청이 딸려있는 상황
        given(applicationExpirationMapper.selectDueApplicationsForUpdate(any(LocalDateTime.class), eq(200)))
                .willReturn(List.of(1L));
        given(applicationExpirationMapper.expirePaymentPendingApplication(eq(1L), any(LocalDateTime.class)))
                .willReturn(1);

        // when
        applicationExpirationService.expireDueApplications(200);

        // then: 딸린 취소 요청도 같이 종료 처리 시도했는지 확인
        verify(applicationMapper).closeRequestedCancelRequestByApplicationId(eq(1L), any(LocalDateTime.class));

    }

    @Test
    @DisplayName("조회 이후 동시 처리로 UPDATE가 0행 반영된 건은 카운트하지 않는다")
    void skipsConcurrentlyModifiedApplications() {

        // given: 조회 시점엔 대상이었지만, UPDATE 시점엔 이미 다른 경로(예: 결제 완료)로 상태가 바뀐 상황
        given(applicationExpirationMapper.selectDueApplicationsForUpdate(any(LocalDateTime.class), eq(200)))
                .willReturn(List.of(1L));
        given(applicationExpirationMapper.expirePaymentPendingApplication(eq(1L), any(LocalDateTime.class)))
                .willReturn(0);

        // when
        int expired = applicationExpirationService.expireDueApplications(200);

        // then: 0행 반영됐으니 카운트되면 안 됨
        assertThat(expired).isZero();

    }

    @Test
    @DisplayName("대상 신청서가 없으면 UPDATE를 시도하지 않고 0을 반환한다")
    void returnsZeroWhenNoDueApplications() {

        // given: 조회 결과가 빈 리스트인 상황
        given(applicationExpirationMapper.selectDueApplicationsForUpdate(any(LocalDateTime.class), eq(200)))
                .willReturn(List.of());

        // when
        int expired = applicationExpirationService.expireDueApplications(200);

        // then
        assertThat(expired).isZero();
        verify(applicationExpirationMapper, never())
                .expirePaymentPendingApplication(any(), any(LocalDateTime.class));

    }

    @Test
    @DisplayName("batchSize가 0 이하이면 예외를 던진다")
    void throwsWhenBatchSizeNotPositive() {

        // when & then
        assertThatThrownBy(() -> applicationExpirationService.expireDueApplications(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize must be positive");

        // 검증 실패로 끝났으니 조회 자체가 시도되면 안 됨
        verify(applicationExpirationMapper, never())
                .selectDueApplicationsForUpdate(any(), anyInt());

    }

}
