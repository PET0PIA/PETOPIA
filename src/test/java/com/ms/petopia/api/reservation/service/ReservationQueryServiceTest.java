package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationDetailResponse;
import com.ms.petopia.api.reservation.dto.ReservationListResponse;
import com.ms.petopia.api.reservation.dto.ReservationListRow;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationQueryServiceTest {

    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private ReservationTimeProvider timeProvider;
    @Mock
    private ReservationPetService reservationPetService;
    @InjectMocks
    private ReservationQueryService service;

    @Test
    void returnsOnlyMapperResultsAndMarksUsableQrAndPaymentStatuses() {
        given(reservationMapper.countMyReservations(20L)).willReturn(3L);
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 1, 9, 0));
        given(reservationMapper.selectMyReservations(20L, 0, 20)).willReturn(List.of(
                row(30L, "CONFIRMED", null, null),
                row(31L, "CHECKED_IN", null, LocalDateTime.of(2026, 8, 2, 10, 5)),
                row(32L, "PENDING_PAYMENT", LocalDateTime.of(2026, 8, 1, 9, 10), null)
        ));

        ReservationListResponse response = service.getMyReservations(20L, 0, 20);

        assertThat(response.items()).hasSize(3);
        assertThat(response.items()).extracting(item -> item.qrAvailable())
                .containsExactly(true, true, false);
        assertThat(response.items()).extracting(item -> item.paymentAvailable())
                .containsExactly(false, false, true);
        assertThat(response.items().get(1).checkedInAt()).isEqualTo(LocalDateTime.of(2026, 8, 2, 10, 5));
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        verify(reservationMapper).selectMyReservations(20L, 0, 20);
    }

    @Test
    void marksReservationAsEndedAfterEntryEndTime() {
        given(reservationMapper.countMyReservations(20L)).willReturn(1L);
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 2, 18, 0, 1));
        given(reservationMapper.selectMyReservations(20L, 0, 20)).willReturn(List.of(
                row(30L, "CONFIRMED", null, null)
        ));

        ReservationListResponse response = service.getMyReservations(20L, 0, 20);

        assertThat(response.items().getFirst().isEnded()).isTrue();
        assertThat(response.items().getFirst().qrAvailable()).isFalse();
    }

    @Test
    void rejectsInvalidUserId() {
        assertThatThrownBy(() -> service.getMyReservations(0L, 0, 20))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void doesNotAllowPaymentResumeAtExactDeadline() {
        given(reservationMapper.countMyReservations(20L)).willReturn(1L);
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 1, 9, 0);
        given(timeProvider.now()).willReturn(deadline);
        given(reservationMapper.selectMyReservations(20L, 0, 20)).willReturn(List.of(
                row(30L, "PENDING_PAYMENT", deadline, null)
        ));

        ReservationListResponse response = service.getMyReservations(20L, 0, 20);

        assertThat(response.items().getFirst().paymentAvailable()).isFalse();
    }

    @Test
    void detailOfPaidConfirmedAdvanceAllowsChangeAndCancel() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 1, 9, 0));
        given(reservationMapper.selectReservationForOwner(30L, 20L))
                .willReturn(detailRow("CONFIRMED", "ADVANCE", 10_000, null));

        ReservationDetailResponse detail = service.getReservationDetail(30L, 20L);

        assertThat(detail.reservationNo()).isEqualTo("R20260802-0030");
        assertThat(detail.fairId()).isEqualTo(7L);
        assertThat(detail.reservationType()).isEqualTo("ADVANCE");
        assertThat(detail.qrAvailable()).isTrue();
        assertThat(detail.canChangeVisitDate()).isTrue();
        // 취소 API가 예약금을 전액 환불하고 취소하므로 유료 확정도 취소 가능하다.
        assertThat(detail.canCancel()).isTrue();
    }

    @Test
    void detailOfFreeConfirmedAdvanceAllowsCancel() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 1, 9, 0));
        given(reservationMapper.selectReservationForOwner(30L, 20L))
                .willReturn(detailRow("CONFIRMED", "ADVANCE", 0, null));

        ReservationDetailResponse detail = service.getReservationDetail(30L, 20L);

        assertThat(detail.canChangeVisitDate()).isTrue();
        assertThat(detail.canCancel()).isTrue();
    }

    @Test
    void detailOfPendingPaymentAllowsCancelButNotChange() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 9, 0);
        given(timeProvider.now()).willReturn(now);
        given(reservationMapper.selectReservationForOwner(30L, 20L))
                .willReturn(detailRow("PENDING_PAYMENT", "ADVANCE", 10_000, now.plusMinutes(10)));

        ReservationDetailResponse detail = service.getReservationDetail(30L, 20L);

        assertThat(detail.paymentAvailable()).isTrue();
        assertThat(detail.canCancel()).isTrue();
        assertThat(detail.canChangeVisitDate()).isFalse();
        assertThat(detail.qrAvailable()).isFalse();
    }

    @Test
    void detailOfEndedReservationDisablesAllActions() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 2, 18, 0, 1));
        given(reservationMapper.selectReservationForOwner(30L, 20L))
                .willReturn(detailRow("CONFIRMED", "ADVANCE", 0, null));

        ReservationDetailResponse detail = service.getReservationDetail(30L, 20L);

        assertThat(detail.isEnded()).isTrue();
        assertThat(detail.qrAvailable()).isFalse();
        assertThat(detail.canChangeVisitDate()).isFalse();
        assertThat(detail.canCancel()).isFalse();
    }

    @Test
    void detailOfEndedPendingPaymentDisablesCancel() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 2, 18, 0, 1));
        given(reservationMapper.selectReservationForOwner(30L, 20L))
                .willReturn(detailRow("PENDING_PAYMENT", "ADVANCE", 10_000, LocalDateTime.of(2026, 8, 2, 18, 10)));

        ReservationDetailResponse detail = service.getReservationDetail(30L, 20L);

        assertThat(detail.isEnded()).isTrue();
        assertThat(detail.canCancel()).isFalse(); // 종료된 예약은 결제대기여도 취소 비활성
    }

    @Test
    void detailNotFoundOrNotOwnedThrows() {
        given(reservationMapper.selectReservationForOwner(30L, 20L)).willReturn(null);

        assertThatThrownBy(() -> service.getReservationDetail(30L, 20L))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    void detailRejectsInvalidReservationId() {
        assertThatThrownBy(() -> service.getReservationDetail(0L, 20L))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void detailWithoutPaymentRowHasNoPaymentFields() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 1, 9, 0));
        given(reservationMapper.selectReservationForOwner(30L, 20L))
                .willReturn(detailRow("CONFIRMED", "ADVANCE", 0, null));

        ReservationDetailResponse detail = service.getReservationDetail(30L, 20L);

        // 무료 예약은 결제 행 자체가 없다. 화면은 두 값이 null이면 결제 줄을 그리지 않는다.
        assertThat(detail.paymentId()).isNull();
        assertThat(detail.paymentMethod()).isNull();
    }

    @Test
    void detailOfCompletedPaymentShowsRealMethod() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 1, 9, 0));
        given(reservationMapper.selectReservationForOwner(30L, 20L)).willReturn(withPayment(
                detailRow("CONFIRMED", "ADVANCE", 10_000, null),
                1042L, "COMPLETED", "카드", null));

        ReservationDetailResponse detail = service.getReservationDetail(30L, 20L);

        assertThat(detail.paymentId()).isEqualTo(1042L);
        assertThat(detail.paymentMethod()).isEqualTo("카드");
    }

    @Test
    void detailOfCompletedEasyPayAppendsProvider() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 1, 9, 0));
        given(reservationMapper.selectReservationForOwner(30L, 20L)).willReturn(withPayment(
                detailRow("CONFIRMED", "ADVANCE", 10_000, null),
                1042L, "COMPLETED", "간편결제", "네이버페이"));

        ReservationDetailResponse detail = service.getReservationDetail(30L, 20L);

        // 간편결제는 대분류만으로는 어디로 결제했는지 알 수 없어 제공사를 함께 붙인다.
        assertThat(detail.paymentMethod()).isEqualTo("간편결제 (네이버페이)");
    }

    @Test
    void detailBeforePaymentCompletionHidesTemporaryMethod() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 9, 0);
        given(timeProvider.now()).willReturn(now);
        given(reservationMapper.selectReservationForOwner(30L, 20L)).willReturn(withPayment(
                detailRow("PENDING_PAYMENT", "ADVANCE", 10_000, now.plusMinutes(10)),
                1042L, "PENDING", "TOSS", null));

        ReservationDetailResponse detail = service.getReservationDetail(30L, 20L);

        // 결제 완료 전 method는 우리가 넣어둔 임시값이라 화면에 그대로 나가면 안 된다.
        assertThat(detail.paymentId()).isEqualTo(1042L);
        assertThat(detail.paymentMethod()).isEqualTo("결제 전");
    }

    @Test
    void detailOfCompletedPaymentWithBlankMethodFallsBack() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 1, 9, 0));
        given(reservationMapper.selectReservationForOwner(30L, 20L)).willReturn(withPayment(
                detailRow("CONFIRMED", "ADVANCE", 10_000, null),
                1042L, "COMPLETED", "   ", null));

        ReservationDetailResponse detail = service.getReservationDetail(30L, 20L);

        // 정상적으로는 안 생기는 조합. 빈 값을 그대로 뿌려 화면을 비워두지 않는다.
        assertThat(detail.paymentMethod()).isEqualTo("결제 전");
    }

    @Test
    void detailOfCanceledReservationKeepsPaidMethod() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 1, 9, 0));
        given(reservationMapper.selectReservationForOwner(30L, 20L)).willReturn(withPayment(
                detailRow("CANCELED", "ADVANCE", 10_000, null),
                1042L, "COMPLETED", "카드", null));

        ReservationDetailResponse detail = service.getReservationDetail(30L, 20L);

        // 환불은 refund 행으로 기록되고 결제 행은 COMPLETED로 남는다. "무엇으로 결제했었나"는
        // 취소 후에도 사용자가 확인해야 하는 정보라 의도적으로 그대로 노출한다.
        assertThat(detail.paymentId()).isEqualTo(1042L);
        assertThat(detail.paymentMethod()).isEqualTo("카드");
    }

    /** detailRow에 결제 행 정보를 얹는다. 결제 행이 없는(무료) 예약은 이 헬퍼를 안 쓴다. */
    private ReservationListRow withPayment(
            ReservationListRow row, Long paymentId, String paymentStatus, String method, String easyPayProvider
    ) {
        row.setPaymentId(paymentId);
        row.setPaymentStatus(paymentStatus);
        row.setPaymentMethod(method);
        row.setEasyPayProvider(easyPayProvider);
        return row;
    }

    private ReservationListRow detailRow(String status, String type, long amount, LocalDateTime paymentExpiresAt) {
        ReservationListRow row = new ReservationListRow();
        row.setReservationId(30L);
        row.setReservationNo("R20260802-0030");
        row.setFairId(7L);
        row.setFairName("서울 펫페어");
        row.setVisitDate(LocalDate.of(2026, 8, 2));
        row.setEntryStartTime(LocalTime.of(10, 0));
        row.setEntryEndTime(LocalTime.of(18, 0));
        row.setReservationStatus(status);
        row.setReservationType(type);
        row.setAmount(amount);
        row.setReservedAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        row.setPaymentExpiresAt(paymentExpiresAt);
        return row;
    }

    private ReservationListRow row(
            Long reservationId,
            String status,
            LocalDateTime paymentExpiresAt,
            LocalDateTime checkedInAt
    ) {
        ReservationListRow row = new ReservationListRow();
        row.setReservationId(reservationId);
        row.setFairName("서울 펫페어");
        row.setVisitDate(LocalDate.of(2026, 8, 2));
        row.setEntryStartTime(LocalTime.of(10, 0));
        row.setEntryEndTime(LocalTime.of(18, 0));
        row.setReservationStatus(status);
        row.setAmount(10_000);
        row.setReservedAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        row.setPaymentExpiresAt(paymentExpiresAt);
        row.setCheckedInAt(checkedInAt);
        return row;
    }
}
