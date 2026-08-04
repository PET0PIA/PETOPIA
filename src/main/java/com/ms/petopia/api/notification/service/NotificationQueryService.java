package com.ms.petopia.api.notification.service;

import com.ms.petopia.api.notification.dto.NotificationListItemResponse;
import com.ms.petopia.api.notification.dto.NotificationListResponse;
import com.ms.petopia.api.notification.mapper.NotificationDeliveryMapper;
import com.ms.petopia.api.notification.mapper.NotificationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {
    private static final int MAX_PAGE_SIZE = 50;
    private final NotificationMapper notificationMapper;
    private final NotificationDeliveryMapper notificationDeliveryMapper;

    @Transactional(readOnly = true)
    public NotificationListResponse getMyNotifications(Long userId, int page, int size){
        if(page < 0 || size <= 0 || size > MAX_PAGE_SIZE){
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        long totalElements = notificationMapper.countByUserId(userId);
        int totalPages = (int) ((totalElements + size - 1)/size);
        List<NotificationListItemResponse> items = notificationMapper
                .selectByUserId(userId, (long) page * size, size)
                .stream()
                .map(row -> new NotificationListItemResponse(
                        row.getNotificationId(), row.getType(), row.getTitle(),
                        row.getBody(), row.getLinkUrl(), row.getCreatedAt(), row.isRead()
                ))
                .toList();
        return new NotificationListResponse(items, page, size, totalElements, totalPages, page + 1 < totalPages);
    }

    @Transactional(readOnly = true)
    public int getUnreadCount(Long userId) {
        return notificationDeliveryMapper.countUnreadInApp(userId);
    }
}
