package com.ms.petopia.api.popup.controller;

import com.ms.petopia.api.popup.dto.request.PopupUpdateRequest;
import com.ms.petopia.api.popup.dto.response.PopupResponse;
import com.ms.petopia.api.popup.service.PopupService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.exception.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminPopupControllerTest {

    @Mock
    private PopupService popupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminPopupController(popupService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addFilters(new TestAuthenticationFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // GET /api/admin/popups - 전체 목록 조회
    @Test
    void getsAllPopups() throws Exception {
        given(popupService.getAll()).willReturn(
                List.of(PopupResponse.builder().popupId(1L).title("테스트 팝업").build()));

        mockMvc.perform(get("/api/admin/popups").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].popupId").value(1));
    }

    // GET /api/admin/popups/{popupId} - 단건 조회
    @Test
    void getsPopupById() throws Exception {
        given(popupService.getById(1L)).willReturn(
                PopupResponse.builder().popupId(1L).title("테스트 팝업").build());

        mockMvc.perform(get("/api/admin/popups/1").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.popupId").value(1));
    }

    // GET /api/admin/popups/{popupId} - 없는 팝업 -> 404 + AD002
    @Test
    void returns404WhenPopupNotFound() throws Exception {
        willThrow(new CommonException(ErrorCode.POPUP_NOT_FOUND))
                .given(popupService).getById(999L);

        mockMvc.perform(get("/api/admin/popups/999").with(authenticatedAs(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AD002"));
    }

    // POST /api/admin/popups - 정상 등록 (201)
    @Test
    void createsPopup() throws Exception {
        given(popupService.create(eq(1L), any())).willReturn(
                PopupResponse.builder().popupId(1L).title("신규 팝업").build());

        mockMvc.perform(post("/api/admin/popups")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "신규 팝업",
                                    "imageKey": "uploads/image/new.jpg",
                                    "linkTarget": "SELF",
                                    "width": 400,
                                    "height": 300
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("신규 팝업"));
    }

    // POST /api/admin/popups - title 누락 -> 400
    @Test
    void returns400WhenTitleBlank() throws Exception {
        mockMvc.perform(post("/api/admin/popups")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "imageKey": "uploads/image/new.jpg",
                                    "linkTarget": "SELF",
                                    "width": 400,
                                    "height": 300
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // POST /api/admin/popups - imageKey, subtitle 둘 다 없음 -> 400
    @Test
    void returns400WhenNoImageAndNoSubtitle() throws Exception {
        mockMvc.perform(post("/api/admin/popups")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "신규 팝업",
                                    "linkTarget": "SELF"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // POST /api/admin/popups - linkLabel만 있고 linkUrl 없음 -> 400
    @Test
    void returns400WhenLinkLabelWithoutLinkUrl() throws Exception {
        mockMvc.perform(post("/api/admin/popups")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "신규 팝업",
                                    "imageKey": "uploads/image/new.jpg",
                                    "linkTarget": "SELF",
                                    "linkLabel": "자세히 보기"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // PUT /api/admin/popups/{popupId} - 정상 수정
    @Test
    void updatesPopup() throws Exception {
        given(popupService.update(eq(1L), any())).willReturn(
                PopupResponse.builder().popupId(1L).title("수정된 팝업").build());

        mockMvc.perform(put("/api/admin/popups/1")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "수정된 팝업"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 팝업"));

        ArgumentCaptor<PopupUpdateRequest> captor = ArgumentCaptor.forClass(PopupUpdateRequest.class);
        verify(popupService).update(eq(1L), captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("수정된 팝업");
    }

    // PUT /api/admin/popups/{popupId} - width가 양수가 아니면 -> 400
    @Test
    void returns400WhenUpdateWidthNotPositive() throws Exception {
        mockMvc.perform(put("/api/admin/popups/1")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "width": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // DELETE /api/admin/popups/{popupId} - 정상 삭제
    @Test
    void deletesPopup() throws Exception {
        mockMvc.perform(delete("/api/admin/popups/1").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // DELETE /api/admin/popups/{popupId} - 없는 팝업 -> 404
    @Test
    void returns404WhenDeletingNotFound() throws Exception {
        willThrow(new CommonException(ErrorCode.POPUP_NOT_FOUND))
                .given(popupService).delete(999L);

        mockMvc.perform(delete("/api/admin/popups/999").with(authenticatedAs(1L)))
                .andExpect(status().isNotFound());
    }

    // PATCH /api/admin/popups/{popupId}/toggle - 노출 토글
    @Test
    void togglesActive() throws Exception {
        mockMvc.perform(patch("/api/admin/popups/1/toggle")
                        .with(authenticatedAs(1L))
                        .param("isActive", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private RequestPostProcessor authenticatedAs(Long userId) {
        return request -> {
            request.setAttribute("authenticatedUserId", userId);
            return request;
        };
    }

    private static final class TestAuthenticationFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {

            Long userId = (Long) request.getAttribute("authenticatedUserId");
            if (userId != null) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of())
                );
            }
            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
