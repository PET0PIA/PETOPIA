package com.ms.petopia.api.banner.controller;

import com.ms.petopia.api.banner.dto.response.BannerResponse;
import com.ms.petopia.api.banner.service.BannerService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminBannerControllerTest {

    @Mock
    private BannerService bannerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminBannerController(bannerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addFilters(new TestAuthenticationFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // GET /api/admin/banners - 전체 목록 조회
    @Test
    void getsAllBanners() throws Exception {
        given(bannerService.getAll()).willReturn(
                List.of(BannerResponse.builder().bannerId(1L).title("테스트 배너").build()));

        mockMvc.perform(get("/api/admin/banners").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].bannerId").value(1));
    }

    // GET /api/admin/banners/{bannerId} - 단건 조회
    @Test
    void getsBannerById() throws Exception {
        given(bannerService.getById(1L)).willReturn(
                BannerResponse.builder().bannerId(1L).title("테스트 배너").build());

        mockMvc.perform(get("/api/admin/banners/1").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bannerId").value(1));
    }

    // GET /api/admin/banners/{bannerId} - 없는 배너 -> 404 + AD001
    @Test
    void returns404WhenBannerNotFound() throws Exception {
        willThrow(new CommonException(ErrorCode.BANNER_NOT_FOUND))
                .given(bannerService).getById(999L);

        mockMvc.perform(get("/api/admin/banners/999").with(authenticatedAs(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AD001"));
    }

    // POST /api/admin/banners - 정상 등록 (201)
    @Test
    void createsBanner() throws Exception {
        given(bannerService.create(eq(1L), any())).willReturn(
                BannerResponse.builder().bannerId(1L).title("신규 배너").build());

        mockMvc.perform(post("/api/admin/banners")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "신규 배너",
                                    "imageKey": "uploads/banner/new.jpg",
                                    "linkTarget": "SELF",
                                    "sortOrder": 0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("신규 배너"));
    }

    // POST /api/admin/banners - title 누락 -> @NotBlank가 400으로 막는지 확인
    @Test
    void returns400WhenTitleBlank() throws Exception {
        mockMvc.perform(post("/api/admin/banners")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "imageKey": "uploads/banner/new.jpg",
                                    "linkTarget": "SELF",
                                    "sortOrder": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // POST /api/admin/banners - linkLabel만 있고 linkUrl 없음 -> 400
    @Test
    void returns400WhenLinkLabelWithoutLinkUrl() throws Exception {
        mockMvc.perform(post("/api/admin/banners")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "신규 배너",
                                    "imageKey": "uploads/banner/new.jpg",
                                    "linkTarget": "SELF",
                                    "linkLabel": "자세히 보기",
                                    "sortOrder": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // POST /api/admin/banners - link2Label만 있고 link2Url 없음 -> 400
    @Test
    void returns400WhenLink2LabelWithoutLink2Url() throws Exception {
        mockMvc.perform(post("/api/admin/banners")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "신규 배너",
                                    "imageKey": "uploads/banner/new.jpg",
                                    "linkTarget": "SELF",
                                    "link2Label": "더 알아보기",
                                    "sortOrder": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // DELETE /api/admin/banners/{bannerId} - 정상 삭제
    @Test
    void deletesBanner() throws Exception {
        mockMvc.perform(delete("/api/admin/banners/1").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // DELETE /api/admin/banners/{bannerId} - 없는 배너 -> 404
    @Test
    void returns404WhenDeletingNotFound() throws Exception {
        willThrow(new CommonException(ErrorCode.BANNER_NOT_FOUND))
                .given(bannerService).delete(999L);

        mockMvc.perform(delete("/api/admin/banners/999").with(authenticatedAs(1L)))
                .andExpect(status().isNotFound());
    }

    // PATCH /api/admin/banners/{bannerId}/toggle - 노출 토글
    @Test
    void togglesActive() throws Exception {
        mockMvc.perform(patch("/api/admin/banners/1/toggle")
                        .with(authenticatedAs(1L))
                        .param("isActive", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // PUT /api/admin/banners/order - 순서 변경
    @Test
    void updatesOrder() throws Exception {
        mockMvc.perform(put("/api/admin/banners/order")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bannerIds\": [3, 1, 2]}"))
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
