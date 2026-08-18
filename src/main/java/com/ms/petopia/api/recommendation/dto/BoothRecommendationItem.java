package com.ms.petopia.api.recommendation.dto;

public record BoothRecommendationItem(
        Long boothId,
        String boothName,
        String reason,

        String hallName,
        String slotNumber

) {
}
