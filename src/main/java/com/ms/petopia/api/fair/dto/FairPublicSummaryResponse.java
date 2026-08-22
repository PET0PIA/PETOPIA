package com.ms.petopia.api.fair.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 행사 공개 요약 조회 응답. 로그인 여부와 무관하게 누구나 볼 수 있는 화면(티켓 예매 화면 등)
 * 전용이다 - {@link FairApplicationDetailResponse}와 달리 managerName/managerPhone/
 * managerEmail(PII), reviewedAt/rejectReason/paymentDueAt(내부 심사 정보), applicantUserId를
 * 전부 뺐다.
 *
 * <p>공개(publish)되지 않은 행사는 아직 심사·결제 대기 중이라 외부에 노출할 이유가 없어
 * {@link com.ms.petopia.api.fair.service.FairService#getPublicSummary}가 조회 자체를
 * 막는다(존재하지 않는 것과 동일하게 404) - 이 응답 자체에는 그 여부를 담지 않는다.
 *
 * <p>latitude/longitude는 지오코딩 실패 시 둘 다 null일 수 있다 - 프론트는 null이면 지도를
 * 안 그리고 주소 텍스트만 보여주면 된다({@link com.ms.petopia.api.fair.service.KakaoGeocodingClient}
 * 참고, fail-soft).
 */
public record FairPublicSummaryResponse(
        Long fairId,
        String name,
        String description,
        String category,
        String posterImageUrl,
        String noticeText,
        /** 반려동물 동반 가능 여부. 예약 화면이 반려동물 선택 UI를 띄울지 판단하는 기준이다. */
        boolean petAllowed,
        String placeName,
        String address,
        String indoorOutdoor,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDate operationStartDate,
        LocalDate operationEndDate,
        String status
) {
}
