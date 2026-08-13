package com.ms.petopia.api.recommendation.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BoothRecommendationRequest {

    private Long petId;

    @Size(max = 100)
    private String need;

}
