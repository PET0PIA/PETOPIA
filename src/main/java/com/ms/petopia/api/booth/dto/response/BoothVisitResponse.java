package com.ms.petopia.api.booth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// GET /api/booths/visits?fairId= 응답 항목 하나 - 그 행사에서 내가 방문한 부스
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BoothVisitResponse {

    private Long boothId;
    private String name;
    private String imageUrl;
    private LocalDateTime firstVisitedAt;
    private Integer visitCount;

}
