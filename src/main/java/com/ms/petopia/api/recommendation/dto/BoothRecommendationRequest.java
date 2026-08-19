package com.ms.petopia.api.recommendation.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class BoothRecommendationRequest {

    //여러 마리 선택 가능. 로그인 + 전부 본인 소유여야 한다(하나라도 아니면 403).
    private List<Long> petIds;

    @Size(max = 100)
    private String need;

}
