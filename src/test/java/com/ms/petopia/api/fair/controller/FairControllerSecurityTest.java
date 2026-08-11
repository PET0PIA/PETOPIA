package com.ms.petopia.api.fair.controller;

import com.ms.petopia.api.auth.mapper.FairAdminAssignmentMapper;
import com.ms.petopia.api.fair.dto.AssignedFairSummary;
import com.ms.petopia.api.fair.service.FairService;
import com.ms.petopia.global.security.SecurityConfig;
import com.ms.petopia.global.security.jwt.JwtAuthenticationFilter;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/fairs/mine-assigned 엔드포인트의 role 기반 접근 제어를 검증한다.
 * SecurityConfig에 추가된 EVENT_ADMIN 전용 규칙이 올바르게 동작하는지 확인하는 데 집중하며,
 * 비즈니스 로직은 FairService/FairAdminAssignmentMapper를 mock으로 처리한다.
 */
@WebMvcTest(
        controllers = FairController.class,
        properties = {
                "jwt.secret=c2VjdXJlLXRlc3Qta2V5LXRlc3Qta2V5LXRlc3Qta2V5LXRlc3Qta2V5",
                "jwt.access-token-expiration=3600000",
                "jwt.refresh-token-expiration=1209600000"
        }
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class FairControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private FairService fairService;

    @MockitoBean
    private FairAdminAssignmentMapper fairAdminAssignmentMapper;

    @Test
    void getAssignedFairs_미인증이면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/fairs/mine-assigned"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(fairAdminAssignmentMapper);
    }

    @Test
    void getAssignedFairs_EVENT_ADMIN이면_배정된_행사_목록을_반환한다() throws Exception {
        given(fairAdminAssignmentMapper.selectByAdminUserId(20L))
                .willReturn(List.of(
                        new AssignedFairSummary(1L, "PETOPIA 2026 봄"),
                        new AssignedFairSummary(2L, "PETOPIA 2026 여름")
                ));

        mockMvc.perform(get("/api/fairs/mine-assigned")
                        .header("Authorization", bearerToken(20L, "EVENT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fairId").value(1))
                .andExpect(jsonPath("$[0].name").value("PETOPIA 2026 봄"))
                .andExpect(jsonPath("$[1].fairId").value(2))
                .andExpect(jsonPath("$[1].name").value("PETOPIA 2026 여름"));
    }

    @Test
    void getAssignedFairs_EVENT_ADMIN이지만_배정된_행사_없으면_빈_배열을_반환한다() throws Exception {
        given(fairAdminAssignmentMapper.selectByAdminUserId(20L)).willReturn(List.of());

        mockMvc.perform(get("/api/fairs/mine-assigned")
                        .header("Authorization", bearerToken(20L, "EVENT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getAssignedFairs_SUPER_ADMIN이면_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/fairs/mine-assigned")
                        .header("Authorization", bearerToken(10L, "SUPER_ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(fairAdminAssignmentMapper);
    }

    @Test
    void getAssignedFairs_일반USER이면_403을_반환한다() throws Exception {
        mockMvc.perform(get("/api/fairs/mine-assigned")
                        .header("Authorization", bearerToken(30L, "USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(fairAdminAssignmentMapper);
    }

    private String bearerToken(Long userId, String role) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(userId, role);
    }
}
