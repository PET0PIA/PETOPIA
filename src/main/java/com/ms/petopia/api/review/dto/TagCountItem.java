package com.ms.petopia.api.review.dto;

public record TagCountItem(
        Long tagId,
        String label,
        long count,
        double ratio
) {
}
