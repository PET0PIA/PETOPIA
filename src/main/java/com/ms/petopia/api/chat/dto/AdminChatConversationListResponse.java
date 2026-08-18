package com.ms.petopia.api.chat.dto;

import java.util.List;

/** 대기열 목록. 필드 구성은 다른 도메인 목록 응답(NotificationListResponse)과 맞춘다. */
public record AdminChatConversationListResponse(
        List<AdminChatConversationSummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
