package com.ms.petopia.api.notification.controller;

import com.ms.petopia.api.notification.dto.*;
import com.ms.petopia.api.notification.service.NotificationQueryService;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock NotificationService notificationService;
    @Mock NotificationQueryService notificationQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(ctx);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(notificationService, notificationQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/notifications → 201 Created + notificationId 반환")
    void saveNotification_returns201() throws Exception {
        given(notificationService.save(any())).willReturn(new SaveNotificationDto.Response(42L));

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "recipientType": "USER",
                                  "type": "PAYMENT_COMPLETED",
                                  "title": "결제 완료",
                                  "body": "결제가 완료됐습니다.",
                                  "channels": ["IN_APP"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.notificationId").value(42));
    }

    @Test
    @DisplayName("userId가 null이면 400 반환")
    void saveNotification_nullUserId_returns400() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": null,
                                  "recipientType": "USER",
                                  "type": "PAYMENT_COMPLETED",
                                  "title": "제목",
                                  "body": "내용",
                                  "channels": ["IN_APP"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("title이 빈 문자열이면 400 반환")
    void saveNotification_blankTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "recipientType": "USER",
                                  "type": "PAYMENT_COMPLETED",
                                  "title": "",
                                  "body": "내용",
                                  "channels": ["IN_APP"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("channels가 비어있으면 400 반환")
    void saveNotification_emptyChannels_returns400() throws Exception {
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "recipientType": "USER",
                                  "type": "PAYMENT_COMPLETED",
                                  "title": "제목",
                                  "body": "내용",
                                  "channels": []
                                }
                                """))
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

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].notificationId").value(1))
                .andExpect(jsonPath("$.data.items[0].isRead").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("PUT /api/notifications/read-all → 200 OK + success true")
    void markAllAsRead_returns200() throws Exception {
        doNothing().when(notificationService).markAllAsRead(1L);

        mockMvc.perform(put("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(notificationService).markAllAsRead(1L);
    }

    @Test
    void markAsRead_returns200() throws Exception {
        doNothing().when(notificationService).markAsRead(10L, 1L);
        mockMvc.perform(put("/api/notifications/10/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(notificationService).markAsRead(10L, 1L);
    }

    @Test
    void getUnreadCount_returns200() throws Exception {
        given(notificationQueryService.getUnreadCount(1L)).willReturn(5);
        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(5));
    }
}
