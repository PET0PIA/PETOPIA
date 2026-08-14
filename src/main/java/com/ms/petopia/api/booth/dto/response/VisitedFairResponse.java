package com.ms.petopia.api.booth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// GET /api/booths/visits/fairs 응답 항목 하나 - 내가 방문한 적 있는 행사
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VisitedFairResponse {

    private Long fairId;
    private String fairName;

}
