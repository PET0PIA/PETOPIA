package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.BoothLayoutResponse;
import com.ms.petopia.api.fair.dto.BoothSlot;
import com.ms.petopia.api.fair.dto.BoothSlotItem;
import com.ms.petopia.api.fair.dto.BoothSlotResponse;
import com.ms.petopia.api.fair.dto.BulkSaveBoothSlotsRequest;
import com.ms.petopia.api.fair.dto.Hall;
import com.ms.petopia.api.fair.mapper.BoothSlotMapper;
import com.ms.petopia.api.fair.mapper.HallMapper;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BoothSlotServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long OTHER_FAIR_ID = 20L;
    private static final Long HALL_ID = 100L;
    private static final Long SLOT_ID = 1000L;
    private static final Long NEW_SLOT_ID = 1001L;
    private static final Long EXPECTED_VERSION = 5L;
    private static final Long STALE_VERSION = 4L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Mock
    private BoothSlotMapper boothSlotMapper;

    @Mock
    private HallMapper hallMapper;

    @Mock
    private FairTimeProvider timeProvider;

    @InjectMocks
    private BoothSlotService boothSlotService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(hallMapper.selectById(HALL_ID)).thenReturn(hall());
        org.mockito.Mockito.lenient().when(timeProvider.now()).thenReturn(NOW);
        // bulkSave는 검증(중복 번호/좌표/가격)을 통과한 요청만 이 낙관적 락 단계까지 도달한다.
        // 그 이전에 실패하는 테스트에서는 호출되지 않아 lenient로 둔다.
        org.mockito.Mockito.lenient().when(hallMapper.bumpBoothLayoutVersion(HALL_ID, EXPECTED_VERSION)).thenReturn(1);
    }

    // ===== 공통 =====

    @Test
    @DisplayName("다른 행사 소속 홀에 저장하려 하면 HALL_NOT_FOUND를 던진다")
    void bulkSave_다른행사소속홀이면_예외를_던진다() {
        assertErrorCode(
                () -> boothSlotService.bulkSave(OTHER_FAIR_ID, HALL_ID, request(List.of())),
                ErrorCode.HALL_NOT_FOUND
        );
        verify(boothSlotMapper, never()).insert(any());
    }

    // ===== 동시 편집(낙관적 락) =====

    @Test
    @DisplayName("expectedVersion이 halls.booth_layout_version과 다르면 BOOTH_LAYOUT_VERSION_CONFLICT를 던지고 아무것도 바꾸지 않는다")
    void bulkSave_버전이다르면_예외를_던지고_아무것도_바꾸지않는다() {
        given(hallMapper.bumpBoothLayoutVersion(HALL_ID, STALE_VERSION)).willReturn(0);

        BulkSaveBoothSlotsRequest request = request(List.of(
                item(null, "A-01", "0.1", "0.1", "0.2", "0.2", 10000L, null)
        ), STALE_VERSION);

        assertErrorCode(() -> boothSlotService.bulkSave(FAIR_ID, HALL_ID, request), ErrorCode.BOOTH_LAYOUT_VERSION_CONFLICT);
        verify(boothSlotMapper, never()).selectByHallId(any());
        verify(boothSlotMapper, never()).insert(any());
        verify(boothSlotMapper, never()).update(any());
        verify(boothSlotMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("expectedVersion이 없으면 INVALID_INPUT_VALUE를 던진다")
    void bulkSave_버전이없으면_예외를_던진다() {
        BulkSaveBoothSlotsRequest request = request(List.of(
                item(null, "A-01", "0.1", "0.1", "0.2", "0.2", 10000L, null)
        ), null);

        assertErrorCode(() -> boothSlotService.bulkSave(FAIR_ID, HALL_ID, request), ErrorCode.INVALID_INPUT_VALUE);
        verify(hallMapper, never()).bumpBoothLayoutVersion(any(), any());
    }

    @Test
    @DisplayName("저장에 성공하면 버전을 1 증가시켜 응답에 담는다")
    void bulkSave_성공하면_버전을_1증가시켜_반환한다() {
        given(boothSlotMapper.selectByHallId(HALL_ID)).willReturn(List.of());
        willAnswer(invocation -> {
            BoothSlot slot = invocation.getArgument(0);
            slot.setBoothSlotId(NEW_SLOT_ID);
            return 1;
        }).given(boothSlotMapper).insert(any(BoothSlot.class));

        BoothLayoutResponse result = boothSlotService.bulkSave(FAIR_ID, HALL_ID, request(List.of(
                item(null, "A-01", "0.1", "0.1", "0.2", "0.2", 10000L, null)
        )));

        assertThat(result.boothLayoutVersion()).isEqualTo(EXPECTED_VERSION + 1);
        verify(hallMapper).bumpBoothLayoutVersion(HALL_ID, EXPECTED_VERSION);
    }

    // ===== 생성 =====

    @Test
    @DisplayName("boothSlotId 없는 항목은 새 슬롯으로 생성한다")
    void bulkSave_신규항목이면_생성한다() {
        given(boothSlotMapper.selectByHallId(HALL_ID)).willReturn(List.of());
        willAnswer(invocation -> {
            BoothSlot slot = invocation.getArgument(0);
            slot.setBoothSlotId(NEW_SLOT_ID);
            return 1;
        }).given(boothSlotMapper).insert(any(BoothSlot.class));

        BulkSaveBoothSlotsRequest request = request(List.of(
                item(null, "A-01", "0.1", "0.1", "0.2", "0.2", 10000L, null)
        ));

        List<BoothSlotResponse> responses = boothSlotService.bulkSave(FAIR_ID, HALL_ID, request).slots();

        assertThat(responses).hasSize(1);
        BoothSlotResponse response = responses.get(0);
        assertThat(response.boothSlotId()).isEqualTo(NEW_SLOT_ID);
        assertThat(response.slotNumber()).isEqualTo("A-01");
        assertThat(response.active()).isTrue();
        assertThat(response.lockedAt()).isNull();

        verify(boothSlotMapper).insert(any(BoothSlot.class));
        verify(boothSlotMapper, never()).update(any());
        verify(boothSlotMapper, never()).deleteById(any());
    }

    // ===== 수정 =====

    @Test
    @DisplayName("잠기지 않은 기존 슬롯은 위치·번호·가격을 자유롭게 수정한다")
    void bulkSave_잠기지않은슬롯이면_전부수정한다() {
        given(boothSlotMapper.selectByHallId(HALL_ID)).willReturn(List.of(existingSlot(null)));

        BulkSaveBoothSlotsRequest request = request(List.of(
                item(SLOT_ID, "A-02", "0.3", "0.3", "0.2", "0.2", 20000L, "콘센트 추가")
        ));

        List<BoothSlotResponse> responses = boothSlotService.bulkSave(FAIR_ID, HALL_ID, request).slots();

        BoothSlotResponse response = responses.get(0);
        assertThat(response.slotNumber()).isEqualTo("A-02");
        assertThat(response.price()).isEqualTo(20000L);
        assertThat(response.memo()).isEqualTo("콘센트 추가");

        ArgumentCaptor<BoothSlot> captor = ArgumentCaptor.forClass(BoothSlot.class);
        verify(boothSlotMapper).update(captor.capture());
        assertThat(captor.getValue().getSlotNumber()).isEqualTo("A-02");
        assertThat(captor.getValue().getPrice()).isEqualTo(20000L);
        // mapper에 넘기는 updatedAt이 응답에 담기는 값(now)과 같은지 - DB의 NOW()에 맡기면
        // 이 값과 실제 저장값이 어긋날 수 있어서 앱이 정한 시각을 명시적으로 실어 보낸다.
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("잠긴 슬롯의 위치를 바꾸려 하면 BOOTH_SLOT_LOCKED를 던지고 갱신하지 않는다")
    void bulkSave_잠긴슬롯위치변경시도하면_예외를_던진다() {
        given(boothSlotMapper.selectByHallId(HALL_ID)).willReturn(List.of(existingSlot(NOW.minusDays(1))));

        BulkSaveBoothSlotsRequest request = request(List.of(
                item(SLOT_ID, "A-02", "0.1", "0.1", "0.2", "0.2", 10000L, null)
        ));

        assertErrorCode(() -> boothSlotService.bulkSave(FAIR_ID, HALL_ID, request), ErrorCode.BOOTH_SLOT_LOCKED);
        verify(boothSlotMapper, never()).update(any());
    }

    @Test
    @DisplayName("잠긴 슬롯이라도 위치·번호·가격이 그대로면 memo만 바꿔 수정을 허용한다")
    void bulkSave_잠긴슬롯이라도_핵심값동일하면_memo만수정한다() {
        given(boothSlotMapper.selectByHallId(HALL_ID)).willReturn(List.of(existingSlot(NOW.minusDays(1))));

        BulkSaveBoothSlotsRequest request = request(List.of(
                item(SLOT_ID, "A-01", "0.1", "0.1", "0.2", "0.2", 10000L, "변경된 메모")
        ));

        List<BoothSlotResponse> responses = boothSlotService.bulkSave(FAIR_ID, HALL_ID, request).slots();

        assertThat(responses.get(0).slotNumber()).isEqualTo("A-01");
        assertThat(responses.get(0).memo()).isEqualTo("변경된 메모");
        assertThat(responses.get(0).lockedAt()).isEqualTo(NOW.minusDays(1));

        ArgumentCaptor<BoothSlot> captor = ArgumentCaptor.forClass(BoothSlot.class);
        verify(boothSlotMapper).update(captor.capture());
        assertThat(captor.getValue().getSlotNumber()).isNull();
        assertThat(captor.getValue().getMemo()).isEqualTo("변경된 메모");
    }

    // ===== 삭제 =====

    @Test
    @DisplayName("요청에서 빠진 잠기지 않은 슬롯은 삭제한다")
    void bulkSave_요청에서빠진잠기지않은슬롯은_삭제한다() {
        given(boothSlotMapper.selectByHallId(HALL_ID)).willReturn(List.of(existingSlot(null)));

        List<BoothSlotResponse> responses = boothSlotService.bulkSave(
                FAIR_ID, HALL_ID, request(List.of())
        ).slots();

        assertThat(responses).isEmpty();
        verify(boothSlotMapper).deleteById(SLOT_ID);
    }

    @Test
    @DisplayName("요청에서 빠진 잠긴 슬롯은 삭제하지 않고 BOOTH_SLOT_LOCKED를 던진다")
    void bulkSave_요청에서빠진잠긴슬롯은_예외를_던진다() {
        given(boothSlotMapper.selectByHallId(HALL_ID)).willReturn(List.of(existingSlot(NOW.minusDays(1))));

        assertErrorCode(
                () -> boothSlotService.bulkSave(FAIR_ID, HALL_ID, request(List.of())),
                ErrorCode.BOOTH_SLOT_LOCKED
        );
        verify(boothSlotMapper, never()).deleteById(any());
    }

    // ===== 검증 =====

    @Test
    @DisplayName("존재하지 않는 boothSlotId를 참조하면 BOOTH_SLOT_NOT_FOUND를 던진다")
    void bulkSave_존재하지않는슬롯참조하면_예외를_던진다() {
        given(boothSlotMapper.selectByHallId(HALL_ID)).willReturn(List.of());

        BulkSaveBoothSlotsRequest request = request(List.of(
                item(9999L, "A-01", "0.1", "0.1", "0.2", "0.2", 10000L, null)
        ));

        assertErrorCode(() -> boothSlotService.bulkSave(FAIR_ID, HALL_ID, request), ErrorCode.BOOTH_SLOT_NOT_FOUND);
    }

    @Test
    @DisplayName("요청 안에 부스 번호가 중복되면 BOOTH_SLOT_DUPLICATE_NUMBER를 던진다")
    void bulkSave_번호중복이면_예외를_던진다() {
        BulkSaveBoothSlotsRequest request = request(List.of(
                item(null, "A-01", "0.1", "0.1", "0.2", "0.2", 10000L, null),
                item(null, "A-01", "0.5", "0.5", "0.2", "0.2", 10000L, null)
        ));

        assertErrorCode(() -> boothSlotService.bulkSave(FAIR_ID, HALL_ID, request), ErrorCode.BOOTH_SLOT_DUPLICATE_NUMBER);
        verify(boothSlotMapper, never()).insert(any());
    }

    @Test
    @DisplayName("요청 안에 같은 boothSlotId를 두 번 이상 참조하면 BOOTH_SLOT_DUPLICATE_REFERENCE를 던진다")
    void bulkSave_슬롯ID중복참조하면_예외를_던진다() {
        BulkSaveBoothSlotsRequest request = request(List.of(
                item(SLOT_ID, "A-01", "0.1", "0.1", "0.2", "0.2", 10000L, null),
                item(SLOT_ID, "A-02", "0.5", "0.5", "0.2", "0.2", 10000L, null)
        ));

        assertErrorCode(() -> boothSlotService.bulkSave(FAIR_ID, HALL_ID, request), ErrorCode.BOOTH_SLOT_DUPLICATE_REFERENCE);
        verify(boothSlotMapper, never()).selectByHallId(any());
        verify(boothSlotMapper, never()).update(any());
    }

    @Test
    @DisplayName("좌표가 0~1 범위를 벗어나면 INVALID_INPUT_VALUE를 던진다")
    void bulkSave_좌표범위벗어나면_예외를_던진다() {
        BulkSaveBoothSlotsRequest request = request(List.of(
                item(null, "A-01", "1.5", "0.1", "0.2", "0.2", 10000L, null)
        ));

        assertErrorCode(() -> boothSlotService.bulkSave(FAIR_ID, HALL_ID, request), ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("가격이 음수면 INVALID_INPUT_VALUE를 던진다")
    void bulkSave_가격음수면_예외를_던진다() {
        BulkSaveBoothSlotsRequest request = request(List.of(
                item(null, "A-01", "0.1", "0.1", "0.2", "0.2", -1L, null)
        ));

        assertErrorCode(() -> boothSlotService.bulkSave(FAIR_ID, HALL_ID, request), ErrorCode.INVALID_INPUT_VALUE);
    }

    // ===== 조회 =====

    @Test
    @DisplayName("정상 조회 시 응답 목록과 현재 버전으로 매핑한다")
    void getBoothSlots_정상조회() {
        given(boothSlotMapper.selectByHallId(HALL_ID)).willReturn(List.of(existingSlot(null)));

        BoothLayoutResponse result = boothSlotService.getBoothSlots(FAIR_ID, HALL_ID);

        assertThat(result.slots()).hasSize(1);
        assertThat(result.slots().get(0).boothSlotId()).isEqualTo(SLOT_ID);
        assertThat(result.boothLayoutVersion()).isEqualTo(EXPECTED_VERSION);
    }

    @Test
    @DisplayName("다른 행사 소속 홀을 조회하면 HALL_NOT_FOUND를 던진다")
    void getBoothSlots_다른행사소속홀이면_예외를_던진다() {
        assertErrorCode(() -> boothSlotService.getBoothSlots(OTHER_FAIR_ID, HALL_ID), ErrorCode.HALL_NOT_FOUND);
    }

    // ===== fixtures =====

    private Hall hall() {
        Hall hall = new Hall();
        hall.setHallId(HALL_ID);
        hall.setFairId(FAIR_ID);
        hall.setName("A홀");
        hall.setBoothLayoutVersion(EXPECTED_VERSION);
        return hall;
    }

    private BoothSlot existingSlot(LocalDateTime lockedAt) {
        BoothSlot slot = new BoothSlot();
        slot.setBoothSlotId(SLOT_ID);
        slot.setHallId(HALL_ID);
        slot.setSlotNumber("A-01");
        slot.setPosX(new BigDecimal("0.1"));
        slot.setPosY(new BigDecimal("0.1"));
        slot.setWidth(new BigDecimal("0.2"));
        slot.setHeight(new BigDecimal("0.2"));
        slot.setPrice(10000L);
        slot.setActive(true);
        slot.setMemo("기존 메모");
        slot.setLockedAt(lockedAt);
        slot.setCreatedAt(NOW.minusDays(2));
        slot.setUpdatedAt(NOW.minusDays(2));
        return slot;
    }

    private BoothSlotItem item(
            Long boothSlotId, String slotNumber, String posX, String posY, String width, String height,
            Long price, String memo
    ) {
        return new BoothSlotItem(
                boothSlotId, slotNumber,
                new BigDecimal(posX), new BigDecimal(posY), new BigDecimal(width), new BigDecimal(height),
                price, memo
        );
    }

    private BulkSaveBoothSlotsRequest request(List<BoothSlotItem> items) {
        return request(items, EXPECTED_VERSION);
    }

    private BulkSaveBoothSlotsRequest request(List<BoothSlotItem> items, Long expectedVersion) {
        return new BulkSaveBoothSlotsRequest(items, expectedVersion);
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
