package com.ms.petopia.api.fair.dto;

import java.util.List;

/**
 * 특정 홀의 부스 슬롯 레이아웃 전체를 한 번에 저장하는 요청.
 * 부스 배치 편집 화면이 캔버스 상태 전체를 매번 다시 보내는 것을 전제로 한다.
 *
 * @param expectedVersion 저장 대상 홀을 조회했을 때 받은 {@code boothLayoutVersion}.
 *                        서버는 이 값과 halls.booth_layout_version이 일치할 때만
 *                        저장을 진행한다 - 낙관적 락으로 동시 편집 시 뒤늦은 저장이
 *                        앞선 저장 내용을 조용히 덮어쓰거나 삭제하는 것을 막는다.
 */
public record BulkSaveBoothSlotsRequest(
        List<BoothSlotItem> slots,
        Long expectedVersion
) {
}
