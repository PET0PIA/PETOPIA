package com.ms.petopia.api.notification.dto;

import java.util.List;

public record NotificationListResponse(
        List<NotificationListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}

