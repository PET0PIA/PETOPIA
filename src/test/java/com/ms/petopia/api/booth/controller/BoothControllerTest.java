package com.ms.petopia.api.booth.controller;

import com.ms.petopia.api.booth.dto.response.BoothItemResponse;
import com.ms.petopia.api.booth.dto.response.BoothResponse;
import com.ms.petopia.api.booth.dto.response.ConfirmedBoothResponse;
import com.ms.petopia.api.booth.service.BoothService;
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

/*
 * BoothController 통합 테스트. BoothService는 Mock으로 대체하고,
 * standaloneSetup + GlobalExceptionHandler로 실제 라우팅/@Valid 검증/
 * 예외→HTTP 상태코드 매핑까지 확인한다. @AuthenticationPrincipal은 business 도메인과
 * 동일한 가짜 인증 필터 패턴으로 시뮬레이션한다.
 */
@ExtendWith(MockitoExtension.class)
class BoothControllerTest {

    private static final String AUTHENTICATED_USER_ID_ATTRIBUTE = "authenticatedUserId";

    @Mock
    private BoothService boothService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(new BoothController(boothService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addFilters(new TestAuthenticationFilter())
                .build();

    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // GET /api/booths/{boothId} - 정상 조회
    @Test
    void getsBoothDetail() throws Exception {

        given(boothService.getBooth(1L, null)).willReturn(
                BoothResponse.builder().boothId(1L).name("멍냥사료 부스").items(List.of()).build());

        mockMvc.perform(get("/api/booths/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boothId").value(1))
                .andExpect(jsonPath("$.data.name").value("멍냥사료 부스"));

    }

    // GET /api/booths/{boothId} - 존재하지 않는 부스 -> 404 + V024
    @Test
    void returns404WhenBoothNotFound() throws Exception {

        willThrow(new CommonException(ErrorCode.BOOTH_NOT_FOUND))
                .given(boothService).getBooth(999L, null);

        mockMvc.perform(get("/api/booths/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V024"));

    }

    // PUT /api/booths/{boothId} - 정상 수정
    @Test
    void updatesBoothProfile() throws Exception {

        given(boothService.updateBooth(eq(1L), eq(1L), any())).willReturn(
                BoothResponse.builder().boothId(1L).name("수정된 이름").build());

        mockMvc.perform(put("/api/booths/1")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"수정된 이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수정된 이름"));

    }

    // PUT /api/booths/{boothId} - 본인 소유 아님 -> 403 + V026
    @Test
    void returns403WhenNotBoothOwner() throws Exception {

        willThrow(new CommonException(ErrorCode.BOOTH_ACCESS_DENIED))
                .given(boothService).updateBooth(eq(2L), eq(1L), any());

        mockMvc.perform(put("/api/booths/1")
                        .with(authenticatedAs(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"수정 시도\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V026"));

    }

    // POST /api/booths/{boothId}/items - 정상 등록 (201)
    @Test
    void addsItem() throws Exception {

        given(boothService.addItem(eq(1L), eq(1L), any())).willReturn(
                BoothItemResponse.builder().boothItemId(1L).boothId(1L).name("체험팩").type("SAMPLE").build());

        mockMvc.perform(post("/api/booths/1/items")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"체험팩\",\"type\":\"SAMPLE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("체험팩"));

    }

    // POST /api/booths/{boothId}/items - name 빈 값 -> @NotBlank가 400으로 막는지 확인
    @Test
    void returns400WhenItemNameBlank() throws Exception {

        mockMvc.perform(post("/api/booths/1/items")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"type\":\"SAMPLE\"}"))
                .andExpect(status().isBadRequest());

    }

    // POST /api/booths/{boothId}/items - type이 enum에 없는 값 -> Jackson 역직렬화 단계에서 400
    @Test
    void returns400WhenItemTypeInvalidEnum() throws Exception {

        mockMvc.perform(post("/api/booths/1/items")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"체험팩\",\"type\":\"FOOD\"}"))
                .andExpect(status().isBadRequest());

    }

    // DELETE /api/booth-items/{boothItemId} - 정상 삭제 (data는 null, success만 확인)
    @Test
    void deletesItem() throws Exception {

        mockMvc.perform(delete("/api/booth-items/1")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

    }

    // DELETE /api/booth-items/{boothItemId} - 존재하지 않는 상품 -> 404 + V025
    @Test
    void returns404WhenItemNotFound() throws Exception {

        willThrow(new CommonException(ErrorCode.BOOTH_ITEM_NOT_FOUND))
                .given(boothService).deleteItem(eq(1L), eq(999L));

        mockMvc.perform(delete("/api/booth-items/999")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V025"));

    }

    // GET /api/fairs/{fairId}/confirmed-booths - 정상 조회
    @Test
    void getsConfirmedBooths() throws Exception {

        given(boothService.getConfirmedBooths(1L)).willReturn(
                List.of(new ConfirmedBoothResponse(1L, "멍냥사료", "A-01", null, null, null, null, null, null, null)));

        mockMvc.perform(get("/api/fairs/1/confirmed-booths"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].businessName").value("멍냥사료"));

    }

    // GET /api/fairs/{fairId}/confirmed-booths - 존재하지 않는 행사 -> 404 + F004
    @Test
    void returns404WhenFairNotFound() throws Exception {

        willThrow(new CommonException(ErrorCode.FAIR_NOT_FOUND))
                .given(boothService).getConfirmedBooths(999L);

        mockMvc.perform(get("/api/fairs/999/confirmed-booths"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("F004"));

    }

    // POST /api/booths/{boothId}/favorites - 정상 추가 (201)
    @Test
    void addsFavorite() throws Exception {

        mockMvc.perform(post("/api/booths/1/favorites")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

    }

    // POST /api/booths/{boothId}/favorites - 존재하지 않는 부스 -> 404 + V024
    @Test
    void returns404WhenFavoritingMissingBooth() throws Exception {

        willThrow(new CommonException(ErrorCode.BOOTH_NOT_FOUND))
                .given(boothService).addFavorite(eq(1L), eq(999L));

        mockMvc.perform(post("/api/booths/999/favorites")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V024"));

    }

    // DELETE /api/booths/{boothId}/favorites - 정상 삭제 (200)
    @Test
    void removesFavorite() throws Exception {

        mockMvc.perform(delete("/api/booths/1/favorites")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

    }

    private RequestPostProcessor authenticatedAs(Long userId) {

        return request -> {

            request.setAttribute(AUTHENTICATED_USER_ID_ATTRIBUTE, userId);
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

            Long userId = (Long) request.getAttribute(AUTHENTICATED_USER_ID_ATTRIBUTE);

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
