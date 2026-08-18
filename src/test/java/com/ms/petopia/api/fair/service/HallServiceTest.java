package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.CreateHallRequest;
import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.Hall;
import com.ms.petopia.api.fair.dto.HallResponse;
import com.ms.petopia.api.fair.dto.UpdateHallRequest;
import com.ms.petopia.api.fair.mapper.BoothSlotMapper;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.fair.mapper.HallMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class HallServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long OTHER_FAIR_ID = 20L;
    private static final Long HALL_ID = 100L;

    @Mock
    private HallMapper hallMapper;

    @Mock
    private FairMapper fairMapper;

    @Mock
    private BoothSlotMapper boothSlotMapper;

    @Mock
    private StorageService storageService;

    @Mock
    private FairAdminAccessGuard fairAdminAccessGuard;

    @InjectMocks
    private HallService hallService;

    // ===== create =====

    @Test
    @DisplayName("존재하지 않는 행사에 홀을 등록하려 하면 FAIR_NOT_FOUND를 던진다")
    void create_행사없으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        assertErrorCode(
                () -> hallService.create(FAIR_ID, new CreateHallRequest("A홀", null)),
                ErrorCode.FAIR_NOT_FOUND
        );
        verify(hallMapper, never()).insert(any());
    }

    @Test
    @DisplayName("홀 이름이 비어 있으면 INVALID_INPUT_VALUE를 던진다")
    void create_이름없으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(new Fair());

        assertErrorCode(
                () -> hallService.create(FAIR_ID, new CreateHallRequest(" ", null)),
                ErrorCode.INVALID_INPUT_VALUE
        );
        verify(hallMapper, never()).insert(any());
    }

    @Test
    @DisplayName("정상 입력이면 홀을 저장하고 응답으로 매핑한다")
    void create_정상입력이면_저장하고_매핑한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(new Fair());
        given(storageService.confirm("tmp/image/abc.jpg", UploadPolicy.IMAGE)).willReturn("uploads/image/abc.jpg");
        given(storageService.toPublicUrl("uploads/image/abc.jpg")).willReturn("https://img");
        willAnswer(invocation -> {
            Hall hall = invocation.getArgument(0);
            hall.setHallId(HALL_ID);
            return 1;
        }).given(hallMapper).insert(any(Hall.class));

        HallResponse response = hallService.create(FAIR_ID, new CreateHallRequest("A홀", "tmp/image/abc.jpg"));

        assertThat(response.hallId()).isEqualTo(HALL_ID);
        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.name()).isEqualTo("A홀");
        assertThat(response.floorPlanImageUrl()).isEqualTo("https://img");
    }

    @Test
    @DisplayName("도면 이미지 객체 키가 없으면 이미지를 확정하지 않고 null로 저장한다")
    void create_이미지객체키없으면_확정을_건너뛴다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(new Fair());
        willAnswer(invocation -> {
            Hall hall = invocation.getArgument(0);
            hall.setHallId(HALL_ID);
            return 1;
        }).given(hallMapper).insert(any(Hall.class));

        HallResponse response = hallService.create(FAIR_ID, new CreateHallRequest("A홀", null));

        assertThat(response.floorPlanImageUrl()).isNull();
        verify(storageService, never()).confirm(any(), any());
    }

    // ===== getHalls =====

    @Test
    @DisplayName("존재하지 않는 행사의 홀 목록을 조회하면 FAIR_NOT_FOUND를 던진다")
    void getHalls_행사없으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);
        assertErrorCode(() -> hallService.getHalls(FAIR_ID), ErrorCode.FAIR_NOT_FOUND);
    }

    @Test
    @DisplayName("홀 목록을 응답 목록으로 매핑한다")
    void getHalls_목록을_매핑한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(new Fair());
        given(hallMapper.selectByFairId(FAIR_ID)).willReturn(List.of(hall(HALL_ID, FAIR_ID), hall(101L, FAIR_ID)));

        List<HallResponse> responses = hallService.getHalls(FAIR_ID);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(HallResponse::hallId).containsExactly(HALL_ID, 101L);
    }

    // ===== getHall =====

    @Test
    @DisplayName("존재하지 않는 홀을 조회하면 HALL_NOT_FOUND를 던진다")
    void getHall_없으면_예외를_던진다() {
        given(hallMapper.selectById(HALL_ID)).willReturn(null);
        assertErrorCode(() -> hallService.getHall(FAIR_ID, HALL_ID), ErrorCode.HALL_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 행사 소속 홀을 조회하면 HALL_NOT_FOUND를 던진다")
    void getHall_다른행사소속이면_예외를_던진다() {
        given(hallMapper.selectById(HALL_ID)).willReturn(hall(HALL_ID, OTHER_FAIR_ID));
        assertErrorCode(() -> hallService.getHall(FAIR_ID, HALL_ID), ErrorCode.HALL_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 행사 소속 홀을 조회하면 응답으로 매핑한다")
    void getHall_같은행사소속이면_매핑한다() {
        given(hallMapper.selectById(HALL_ID)).willReturn(hall(HALL_ID, FAIR_ID));

        HallResponse response = hallService.getHall(FAIR_ID, HALL_ID);

        assertThat(response.hallId()).isEqualTo(HALL_ID);
        assertThat(response.fairId()).isEqualTo(FAIR_ID);
    }

    // ===== update =====

    @Test
    @DisplayName("다른 행사 소속 홀을 수정하려 하면 HALL_NOT_FOUND를 던지고 갱신하지 않는다")
    void update_다른행사소속이면_예외를_던진다() {
        given(hallMapper.selectById(HALL_ID)).willReturn(hall(HALL_ID, OTHER_FAIR_ID));

        assertErrorCode(
                () -> hallService.update(FAIR_ID, HALL_ID, new UpdateHallRequest("변경된 이름", null)),
                ErrorCode.HALL_NOT_FOUND
        );
        verify(hallMapper, never()).update(any());
    }

    @Test
    @DisplayName("같은 행사 소속 홀을 수정하면 null이 아닌 필드만 반영하고 갱신된 홀을 다시 조회해 반환한다")
    void update_정상수정이면_갱신후_다시조회한다() {
        Hall existing = hall(HALL_ID, FAIR_ID);
        Hall afterUpdate = hall(HALL_ID, FAIR_ID);
        afterUpdate.setName("변경된 이름");

        given(hallMapper.selectById(HALL_ID)).willReturn(existing, afterUpdate);

        HallResponse response = hallService.update(FAIR_ID, HALL_ID, new UpdateHallRequest("변경된 이름", null));

        ArgumentCaptor<Hall> captor = ArgumentCaptor.forClass(Hall.class);
        verify(hallMapper).update(captor.capture());
        assertThat(captor.getValue().getHallId()).isEqualTo(HALL_ID);
        assertThat(captor.getValue().getName()).isEqualTo("변경된 이름");

        assertThat(response.name()).isEqualTo("변경된 이름");
    }

    @Test
    @DisplayName("이름을 빈 문자열로 수정하려 하면 INVALID_INPUT_VALUE를 던지고 갱신하지 않는다")
    void update_이름이_빈문자열이면_예외를_던진다() {
        given(hallMapper.selectById(HALL_ID)).willReturn(hall(HALL_ID, FAIR_ID));

        assertErrorCode(
                () -> hallService.update(FAIR_ID, HALL_ID, new UpdateHallRequest(" ", null)),
                ErrorCode.INVALID_INPUT_VALUE
        );
        verify(hallMapper, never()).update(any());
    }

    @Test
    @DisplayName("이름이 null이면(변경 안 함) 검증 없이 통과한다")
    void update_이름이_null이면_검증하지않는다() {
        Hall existing = hall(HALL_ID, FAIR_ID);
        given(hallMapper.selectById(HALL_ID)).willReturn(existing, existing);

        hallService.update(FAIR_ID, HALL_ID, new UpdateHallRequest(null, null));

        ArgumentCaptor<Hall> captor = ArgumentCaptor.forClass(Hall.class);
        verify(hallMapper).update(captor.capture());
        assertThat(captor.getValue().getName()).isNull();
    }

    // ===== delete =====

    @Test
    @DisplayName("다른 행사 소속 홀을 삭제하려 하면 HALL_NOT_FOUND를 던지고 삭제하지 않는다")
    void delete_다른행사소속이면_예외를_던진다() {
        given(hallMapper.selectById(HALL_ID)).willReturn(hall(HALL_ID, OTHER_FAIR_ID));

        assertErrorCode(() -> hallService.delete(FAIR_ID, HALL_ID), ErrorCode.HALL_NOT_FOUND);
        verify(hallMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("부스 슬롯이 남아있으면 HALL_HAS_BOOTH_SLOTS를 던지고 삭제하지 않는다")
    void delete_부스슬롯남아있으면_예외를_던진다() {
        given(hallMapper.selectById(HALL_ID)).willReturn(hall(HALL_ID, FAIR_ID));
        given(boothSlotMapper.existsByHallId(HALL_ID)).willReturn(true);

        assertErrorCode(() -> hallService.delete(FAIR_ID, HALL_ID), ErrorCode.HALL_HAS_BOOTH_SLOTS);
        verify(hallMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("부스 슬롯이 없는 같은 행사 소속 홀을 삭제하면 deleteById를 호출한다")
    void delete_정상삭제() {
        given(hallMapper.selectById(HALL_ID)).willReturn(hall(HALL_ID, FAIR_ID));
        given(boothSlotMapper.existsByHallId(HALL_ID)).willReturn(false);

        hallService.delete(FAIR_ID, HALL_ID);

        verify(hallMapper).deleteById(HALL_ID);
    }

    // ===== fixtures =====

    private Hall hall(Long hallId, Long fairId) {
        Hall hall = new Hall();
        hall.setHallId(hallId);
        hall.setFairId(fairId);
        hall.setName("A홀");
        hall.setFloorPlanImageUrl(null);
        hall.setCreatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        hall.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        return hall;
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
