package com.ms.petopia.api.notification.controller;

import com.ms.petopia.api.notification.dto.NotificationListResponse;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationQueryService;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.reservation.controller.TemporaryAuthHeaders;
import com.ms.petopia.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationQueryService notificationQueryService;

    @PostMapping("/notifications")
    public ResponseEntity<ApiResponse<SaveNotificationDto.Response>> saveNotification(
            @Valid @RequestBody SaveNotificationDto.Request request
    ) {
        SaveNotificationDto.Response response = notificationService.save(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, response));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<NotificationListResponse>> getMyNotifications(
            @RequestHeader(TemporaryAuthHeaders.USER_ID) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(notificationQueryService.getMyNotifications(userId, page, size)));
    }

    @PutMapping("/notifications/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @RequestHeader(TemporaryAuthHeaders.USER_ID) Long userId,
            @PathVariable Long notificationId
    ){
        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/notifications/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @RequestHeader(TemporaryAuthHeaders.USER_ID) Long userId
    ){
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<ApiResponse<Integer>> getUnreadCount(
            @RequestHeader(TemporaryAuthHeaders.USER_ID) Long userId
    ){
        return ResponseEntity.ok(
                ApiResponse.success(notificationQueryService.getUnreadCount(userId))
        );
    }
}
