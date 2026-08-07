package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.CreateFairDateRequest;
import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.FairDate;
import com.ms.petopia.api.fair.dto.FairDateResponse;
import com.ms.petopia.api.fair.dto.FairDateWithStats;
import com.ms.petopia.api.fair.dto.FairStatus;
import com.ms.petopia.api.fair.dto.UpdateFairDateRequest;
import com.ms.petopia.api.fair.mapper.FairDateMapper;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FairDateServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long OTHER_FAIR_ID = 20L;
    private static final Long FAIR_DATE_ID = 100L;
    private static final LocalDate OPERATION_DATE = LocalDate.of(2026, 9, 5);
    private static final LocalTime ENTRY_START = LocalTime.of(10, 0);
    private static final LocalTime ENTRY_END = LocalTime.of(18, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 7, 10, 0);

    @Mock
    private FairDateMapper fairDateMapper;

    @Mock
    private FairMapper fairMapper;

    @Mock
    private FairTimeProvider timeProvider;

    @InjectMocks
    private FairDateService fairDateService;

    @BeforeEach
    void setUpTime() {
        org.mockito.Mockito.lenient().when(timeProvider.now()).thenReturn(NOW);
    }

    // ===== create =====

    @Test
    @DisplayName("존재하지 않는 행사에 운영일을 등록하려 하면 FAIR_NOT_FOUND를 던진다")
    void create_행사없으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        assertErrorCode(
                () -> fairDateService.create(FAIR_ID, createRequest()),
                ErrorCode.FAIR_NOT_FOUND
        );
        verify(fairDateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("필수값이 없으면 INVALID_INPUT_VALUE를 던진다")
    void create_필수값없으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithPeriod(null, null));

        assertErrorCode(
                () -> fairDateService.create(FAIR_ID, new CreateFairDateRequest(null, 100, ENTRY_START, ENTRY_END)),
                ErrorCode.INVALID_INPUT_VALUE
        );
        verify(fairDateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("정원이 0 이하이면 INVALID_INPUT_VALUE를 던진다")
    void create_정원이0이하면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithPeriod(null, null));

        assertErrorCode(
                () -> fairDateService.create(FAIR_ID, new CreateFairDateRequest(OPERATION_DATE, 0, ENTRY_START, ENTRY_END)),
                ErrorCode.INVALID_INPUT_VALUE
        );
        verify(fairDateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("입장 종료 시간이 시작 시간보다 빠르거나 같으면 FAIR_DATE_INVALID_ENTRY_TIME을 던진다")
    void create_입장시간이거꾸로면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithPeriod(null, null));

        assertErrorCode(
                () -> fairDateService.create(FAIR_ID, new CreateFairDateRequest(OPERATION_DATE, 100, ENTRY_END, ENTRY_START)),
                ErrorCode.FAIR_DATE_INVALID_ENTRY_TIME
        );
        verify(fairDateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("운영 날짜가 행사 운영 기간보다 이르면 FAIR_DATE_OUT_OF_OPERATION_PERIOD를 던진다")
    void create_운영기간보다이르면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID))
                .willReturn(fairWithPeriod(OPERATION_DATE.plusDays(1), OPERATION_DATE.plusDays(10)));

        assertErrorCode(
                () -> fairDateService.create(FAIR_ID, createRequest()),
                ErrorCode.FAIR_DATE_OUT_OF_OPERATION_PERIOD
        );
        verify(fairDateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("운영 날짜가 행사 운영 기간보다 늦으면 FAIR_DATE_OUT_OF_OPERATION_PERIOD를 던진다")
    void create_운영기간보다늦으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID))
                .willReturn(fairWithPeriod(OPERATION_DATE.minusDays(10), OPERATION_DATE.minusDays(1)));

        assertErrorCode(
                () -> fairDateService.create(FAIR_ID, createRequest()),
                ErrorCode.FAIR_DATE_OUT_OF_OPERATION_PERIOD
        );
        verify(fairDateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("같은 행사에 같은 운영 날짜가 이미 있으면 FAIR_DATE_DUPLICATE를 던진다")
    void create_중복날짜면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithPeriod(null, null));
        given(fairDateMapper.selectByFairIdAndDate(FAIR_ID, OPERATION_DATE)).willReturn(new FairDate());

        assertErrorCode(
                () -> fairDateService.create(FAIR_ID, createRequest()),
                ErrorCode.FAIR_DATE_DUPLICATE
        );
        verify(fairDateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("정상 입력이면 운영일을 저장하고 예약 집계와 함께 응답으로 매핑한다")
    void create_정상입력이면_저장하고_매핑한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithPeriod(null, null));
        given(fairDateMapper.selectByFairIdAndDate(FAIR_ID, OPERATION_DATE)).willReturn(null);
        willAnswer(invocation -> {
            FairDate fairDate = invocation.getArgument(0);
            fairDate.setFairDateId(FAIR_DATE_ID);
            return 1;
        }).given(fairDateMapper).insert(any(FairDate.class));
        given(fairDateMapper.selectByIdWithStats(FAIR_DATE_ID)).willReturn(statsRow(0, false));

        FairDateResponse response = fairDateService.create(FAIR_ID, createRequest());

        assertThat(response.fairDateId()).isEqualTo(FAIR_DATE_ID);
        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.operationDate()).isEqualTo(OPERATION_DATE);
        assertThat(response.capacity()).isEqualTo(100);
        assertThat(response.reservedCount()).isEqualTo(0);
        assertThat(response.onsiteSalesConfigured()).isFalse();
    }

    @Test
    @DisplayName("취소된 행사에 운영일을 등록하려 하면 FAIR_DATE_FAIR_NOT_EDITABLE을 던진다")
    void create_취소된행사면_예외를_던진다() {
        Fair canceledFair = fairWithPeriod(null, null);
        canceledFair.setCanceledAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        given(fairMapper.selectById(FAIR_ID)).willReturn(canceledFair);

        assertErrorCode(
                () -> fairDateService.create(FAIR_ID, createRequest()),
                ErrorCode.FAIR_DATE_FAIR_NOT_EDITABLE
        );
        verify(fairDateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("종료된 행사에 운영일을 등록하려 하면 FAIR_DATE_FAIR_NOT_EDITABLE을 던진다")
    void create_종료된행사면_예외를_던진다() {
        Fair endedFair = fairWithPeriod(null, null);
        endedFair.setStatus(FairStatus.ENDED);
        given(fairMapper.selectById(FAIR_ID)).willReturn(endedFair);

        assertErrorCode(
                () -> fairDateService.create(FAIR_ID, createRequest()),
                ErrorCode.FAIR_DATE_FAIR_NOT_EDITABLE
        );
        verify(fairDateMapper, never()).insert(any());
    }

    // ===== getFairDates =====

    @Test
    @DisplayName("존재하지 않는 행사의 운영일 목록을 조회하면 FAIR_NOT_FOUND를 던진다")
    void getFairDates_행사없으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);
        assertErrorCode(() -> fairDateService.getFairDates(FAIR_ID), ErrorCode.FAIR_NOT_FOUND);
    }

    @Test
    @DisplayName("운영일 목록을 예약 집계와 함께 응답 목록으로 매핑한다")
    void getFairDates_목록을_매핑한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithPeriod(null, null));
        given(fairDateMapper.selectByFairIdWithStats(FAIR_ID))
                .willReturn(List.of(statsRow(3, true)));

        List<FairDateResponse> responses = fairDateService.getFairDates(FAIR_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).reservedCount()).isEqualTo(3);
        assertThat(responses.get(0).onsiteSalesConfigured()).isTrue();
    }

    // ===== update =====

    @Test
    @DisplayName("다른 행사 소속 운영일을 수정하려 하면 FAIR_DATE_NOT_FOUND를 던지고 갱신하지 않는다")
    void update_다른행사소속이면_예외를_던진다() {
        given(fairDateMapper.selectById(FAIR_DATE_ID)).willReturn(fairDate(OTHER_FAIR_ID));

        assertErrorCode(
                () -> fairDateService.update(FAIR_ID, FAIR_DATE_ID, updateRequest()),
                ErrorCode.FAIR_DATE_NOT_FOUND
        );
        verify(fairDateMapper, never()).update(any());
    }

    @Test
    @DisplayName("정원이 0 이하이면 INVALID_INPUT_VALUE를 던지고 갱신하지 않는다")
    void update_정원이0이하면_예외를_던진다() {
        given(fairDateMapper.selectById(FAIR_DATE_ID)).willReturn(fairDate(FAIR_ID));
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithPeriod(null, null));

        assertErrorCode(
                () -> fairDateService.update(FAIR_ID, FAIR_DATE_ID, new UpdateFairDateRequest(0, ENTRY_START, ENTRY_END)),
                ErrorCode.INVALID_INPUT_VALUE
        );
        verify(fairDateMapper, never()).update(any());
    }

    @Test
    @DisplayName("입장 종료 시간이 시작 시간보다 빠르거나 같으면 FAIR_DATE_INVALID_ENTRY_TIME을 던진다")
    void update_입장시간이거꾸로면_예외를_던진다() {
        given(fairDateMapper.selectById(FAIR_DATE_ID)).willReturn(fairDate(FAIR_ID));
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithPeriod(null, null));

        assertErrorCode(
                () -> fairDateService.update(FAIR_ID, FAIR_DATE_ID, new UpdateFairDateRequest(100, ENTRY_END, ENTRY_START)),
                ErrorCode.FAIR_DATE_INVALID_ENTRY_TIME
        );
        verify(fairDateMapper, never()).update(any());
    }

    @Test
    @DisplayName("같은 행사 소속 운영일을 수정하면 정원·입장시간을 갱신하고 예약 집계와 함께 다시 조회해 반환한다")
    void update_정상수정이면_갱신후_다시조회한다() {
        given(fairDateMapper.selectById(FAIR_DATE_ID)).willReturn(fairDate(FAIR_ID));
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithPeriod(null, null));
        given(fairDateMapper.selectByIdWithStats(FAIR_DATE_ID)).willReturn(statsRow(5, true));

        FairDateResponse response = fairDateService.update(FAIR_ID, FAIR_DATE_ID, updateRequest());

        ArgumentCaptor<FairDate> captor = ArgumentCaptor.forClass(FairDate.class);
        verify(fairDateMapper).update(captor.capture());
        assertThat(captor.getValue().getFairDateId()).isEqualTo(FAIR_DATE_ID);
        assertThat(captor.getValue().getCapacity()).isEqualTo(150);

        assertThat(response.capacity()).isEqualTo(100);
        assertThat(response.reservedCount()).isEqualTo(5);
        assertThat(response.onsiteSalesConfigured()).isTrue();
    }

    @Test
    @DisplayName("취소된 행사의 운영일을 수정하려 하면 FAIR_DATE_FAIR_NOT_EDITABLE을 던지고 갱신하지 않는다")
    void update_취소된행사면_예외를_던진다() {
        given(fairDateMapper.selectById(FAIR_DATE_ID)).willReturn(fairDate(FAIR_ID));
        Fair canceledFair = fairWithPeriod(null, null);
        canceledFair.setCanceledAt(NOW.minusDays(1));
        given(fairMapper.selectById(FAIR_ID)).willReturn(canceledFair);

        assertErrorCode(
                () -> fairDateService.update(FAIR_ID, FAIR_DATE_ID, updateRequest()),
                ErrorCode.FAIR_DATE_FAIR_NOT_EDITABLE
        );
        verify(fairDateMapper, never()).update(any());
    }

    @Test
    @DisplayName("종료된 행사의 운영일을 수정하려 하면 FAIR_DATE_FAIR_NOT_EDITABLE을 던지고 갱신하지 않는다")
    void update_종료된행사면_예외를_던진다() {
        given(fairDateMapper.selectById(FAIR_DATE_ID)).willReturn(fairDate(FAIR_ID));
        Fair endedFair = fairWithPeriod(null, null);
        endedFair.setStatus(FairStatus.ENDED);
        given(fairMapper.selectById(FAIR_ID)).willReturn(endedFair);

        assertErrorCode(
                () -> fairDateService.update(FAIR_ID, FAIR_DATE_ID, updateRequest()),
                ErrorCode.FAIR_DATE_FAIR_NOT_EDITABLE
        );
        verify(fairDateMapper, never()).update(any());
    }

    // ===== delete =====

    @Test
    @DisplayName("다른 행사 소속 운영일을 삭제하려 하면 FAIR_DATE_NOT_FOUND를 던지고 삭제하지 않는다")
    void delete_다른행사소속이면_예외를_던진다() {
        given(fairDateMapper.selectById(FAIR_DATE_ID)).willReturn(fairDate(OTHER_FAIR_ID));

        assertErrorCode(() -> fairDateService.delete(FAIR_ID, FAIR_DATE_ID), ErrorCode.FAIR_DATE_NOT_FOUND);
        verify(fairDateMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("같은 행사 소속 운영일을 삭제하면 예약·현장예매 정책 여부와 무관하게 deleteById를 호출한다")
    void delete_정상삭제() {
        given(fairDateMapper.selectById(FAIR_DATE_ID)).willReturn(fairDate(FAIR_ID));
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithPeriod(null, null));

        fairDateService.delete(FAIR_ID, FAIR_DATE_ID);

        verify(fairDateMapper).deleteById(FAIR_DATE_ID);
    }

    @Test
    @DisplayName("취소된 행사의 운영일을 삭제하려 하면 FAIR_DATE_FAIR_NOT_EDITABLE을 던지고 삭제하지 않는다")
    void delete_취소된행사면_예외를_던진다() {
        given(fairDateMapper.selectById(FAIR_DATE_ID)).willReturn(fairDate(FAIR_ID));
        Fair canceledFair = fairWithPeriod(null, null);
        canceledFair.setCanceledAt(NOW.minusDays(1));
        given(fairMapper.selectById(FAIR_ID)).willReturn(canceledFair);

        assertErrorCode(() -> fairDateService.delete(FAIR_ID, FAIR_DATE_ID), ErrorCode.FAIR_DATE_FAIR_NOT_EDITABLE);
        verify(fairDateMapper, never()).deleteById(any());
    }

    // ===== fixtures =====

    private CreateFairDateRequest createRequest() {
        return new CreateFairDateRequest(OPERATION_DATE, 100, ENTRY_START, ENTRY_END);
    }

    private UpdateFairDateRequest updateRequest() {
        return new UpdateFairDateRequest(150, ENTRY_START, ENTRY_END);
    }

    private Fair fairWithPeriod(LocalDate operationStartDate, LocalDate operationEndDate) {
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        fair.setOperationStartDate(operationStartDate);
        fair.setOperationEndDate(operationEndDate);
        return fair;
    }

    private FairDate fairDate(Long fairId) {
        FairDate fairDate = new FairDate();
        fairDate.setFairDateId(FAIR_DATE_ID);
        fairDate.setFairId(fairId);
        fairDate.setOperationDate(OPERATION_DATE);
        fairDate.setCapacity(100);
        fairDate.setEntryStartTime(ENTRY_START);
        fairDate.setEntryEndTime(ENTRY_END);
        fairDate.setCreatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        fairDate.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        return fairDate;
    }

    private FairDateWithStats statsRow(int reservedCount, boolean onsiteSalesConfigured) {
        FairDateWithStats row = new FairDateWithStats();
        row.setFairDateId(FAIR_DATE_ID);
        row.setFairId(FAIR_ID);
        row.setOperationDate(OPERATION_DATE);
        row.setCapacity(100);
        row.setEntryStartTime(ENTRY_START);
        row.setEntryEndTime(ENTRY_END);
        row.setReservedCount(reservedCount);
        row.setOnsiteSalesConfigured(onsiteSalesConfigured);
        row.setCreatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        row.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        return row;
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
