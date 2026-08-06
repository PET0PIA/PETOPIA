package com.ms.petopia.api.fair.dto;

import java.util.List;

/**
 * 부스 슬롯 조회(GET)·일괄저장(PUT) 공통 응답.
 *
 * @param boothLayoutVersion 이 홀의 부스 배치 낙관적 락 버전. 클라이언트는 이 값을
 *                            들고 있다가 다음 일괄저장 요청의 expectedVersion으로
 *                            그대로 돌려보내야 한다. 조회 이후 다른 곳에서 먼저
 *                            저장했다면 버전이 달라져 있으므로 저장 시 409로 거부된다.
 */
public record BoothLayoutResponse(
        List<BoothSlotResponse> slots,
        Long boothLayoutVersion
) {
}
