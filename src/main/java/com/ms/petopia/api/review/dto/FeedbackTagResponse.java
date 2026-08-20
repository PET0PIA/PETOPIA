package com.ms.petopia.api.review.dto;

public record FeedbackTagResponse(
        Long tagId,
        FeedbackTag.Scope scope,
        String category,
        FeedbackTag.Sentiment sentiment,
        String label,
        Integer sortOrder
) {
    public static FeedbackTagResponse from(FeedbackTag tag) {
        return new FeedbackTagResponse(
                tag.getTagId(),
                tag.getScope(),
                tag.getCategory(),
                tag.getSentiment(),
                tag.getLabel(),
                tag.getSortOrder()
        );
    }
}
