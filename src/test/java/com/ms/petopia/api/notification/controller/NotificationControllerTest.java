package com.ms.petopia.api.notification.controller;

import tools.jackson.databind.ObjectMapper;
import com.ms.petopia.api.notification.dto.*;
import com.ms.petopia.api.notification.service.NotificationQueryService;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // Spring Boot 4.x는 @MockBean 대신 @MockitoBean 사용
    @MockitoBean NotificationService notificationService;
    @MockitoBean NotificationQueryService notificationQueryService;
    // SecurityConfig → JwtAuthenticationFilter → JwtTokenProvider 의존 체인.
    // @WebMvcTest는 일반 @Component를 스캔하지 않으므로 mock으로 등록해야 컨텍스트가 뜬다.
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("POST /api/notifications → 201 Created + notificationId 반환")
    void saveNotification_returns201() throws Exception {
        given(notificationService.save(any())).willReturn(new SaveNotificationDto.Response(42L));

        SaveNotificationDto.Request request = new SaveNotificationDto.Request(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "결제 완료", "결제가 완료됐습니다.", null,
                List.of(DeliveryChannel.IN_APP),
                null
        );

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.notificationId").value(42));
    }

    @Test
    @DisplayName("userId가 null이면 400 반환")
    void saveNotification_nullUserId_returns400() throws Exception {
        String body = """
                {
                  "userId": null,
                  "recipientType": "USER",
                  "type": "PAYMENT_COMPLETED",
                  "title": "제목",
                  "body": "내용",
                  "channels": ["IN_APP"]
                }
                """;

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("title이 빈 문자열이면 400 반환")
    void saveNotification_blankTitle_returns400() throws Exception {
        String body = """
                {
                  "userId": 1,
                  "recipientType": "USER",
                  "type": "PAYMENT_COMPLETED",
                  "title": "",
                  "body": "내용",
                  "channels": ["IN_APP"]
                }
                """;

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("channels가 비어있으면 400 반환")
    void saveNotification_emptyChannels_returns400() throws Exception {
        String body = """
                {
                  "userId": 1,
                  "recipientType": "USER",
                  "type": "PAYMENT_COMPLETED",
                  "title": "제목",
                  "body": "내용",
                  "channels": []
                }
                """;

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("GET /api/notifications → 200 OK + 알림 목록 반환")
    void getMyNotifications_returns200() throws Exception {
        NotificationListResponse response = new NotificationListResponse(
                List.of(new NotificationListItemResponse(
                        1L, "PAYMENT_COMPLETED", "결제 완료", "결제가 완료됐습니다.",
                        null, LocalDateTime.of(2026, 8, 1, 10, 0), false)),
                0, 20, 1L, 1, false
        );
        given(notificationQueryService.getMyNotifications(1L, 0, 20)).willReturn(response);

        mockMvc.perform(get("/api/notifications")
                        .header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].notificationId").value(1))
                .andExpect(jsonPath("$.data.items[0].isRead").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("X-User-Id 헤더 누락 시 400 반환")
    void getMyNotifications_missingHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isBadRequest());
    }

    // ===== PUT /api/notifications/read-all =====

    @Test
    @DisplayName("PUT /api/notifications/read-all → 200 OK + success true")
    void markAllAsRead_returns200() throws Exception {
        doNothing().when(notificationService).markAllAsRead(1L);

        mockMvc.perform(put("/api/notifications/read-all")
                        .header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PUT /api/notifications/read-all X-User-Id 헤더 누락 시 400 반환")
    void markAllAsRead_missingHeader_returns400() throws Exception {
        mockMvc.perform(put("/api/notifications/read-all"))
                .andExpect(status().isBadRequest());
    }
}