package com.ms.petopia.api.notification.controller;

import tools.jackson.databind.ObjectMapper;
import com.ms.petopia.api.notification.dto.*;
import com.ms.petopia.api.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // Spring Boot 4.x는 @MockBean 대신 @MockitoBean 사용
    @MockitoBean NotificationService notificationService;

    @Test
    @DisplayName("POST /api/notifications → 201 Created + notificationId 반환")
    void saveNotification_returns201() throws Exception {
        given(notificationService.save(any())).willReturn(new SaveNotificationResponse(42L));

        SaveNotificationRequest request = new SaveNotificationRequest(
                1L, RecipientType.USER, NotificationType.PAYMENT_COMPLETED,
                "결제 완료", "결제가 완료됐습니다.", null,
                List.of(DeliveryChannel.IN_APP),
                null
        );

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificationId").value(42));
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
}