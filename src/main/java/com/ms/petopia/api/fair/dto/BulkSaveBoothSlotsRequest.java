package com.ms.petopia.api.fair.dto;

import java.util.List;

/**
 * 특정 홀의 부스 슬롯 레이아웃 전체를 한 번에 저장하는 요청.
 * 부스 배치 편집 화면이 캔버스 상태 전체를 매번 다시 보내는 것을 전제로 한다.
 */
public record BulkSaveBoothSlotsRequest(
        List<BoothSlotItem> slots
) {
}
