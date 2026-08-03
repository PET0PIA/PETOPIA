package com.ms.petopia.api.notification.controller;

import com.ms.petopia.api.notification.dto.SaveNotificationRequest;
import com.ms.petopia.api.notification.dto.SaveNotificationResponse;
import com.ms.petopia.api.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    @PostMapping("/notifications")
    public ResponseEntity<SaveNotificationResponse> saveNotification(
            @Valid @RequestBody SaveNotificationRequest request
    ){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.save(request));
    }
}
