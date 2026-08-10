package com.ms.petopia.api.application.controller;

import com.ms.petopia.api.application.dto.response.*;
import com.ms.petopia.api.application.service.ApplicationService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * ApplicationController 통합 테스트. ApplicationService는 Mock으로 대체하고,
 * standaloneSetup + GlobalExceptionHandler로 실제 라우팅/@Valid 검증/
 * 예외→HTTP 상태코드 매핑까지 확인한다. 11개 엔드포인트 각각 성공 1건 + 대표 실패 케이스만 다룬다.
 * @AuthenticationPrincipal은 business 도메인(BusinessControllerTest)과 동일한 가짜 인증 필터
 * 패턴으로 시뮬레이션한다.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationControllerTest {

    private static final String AUTHENTICATED_USER_ID_ATTRIBUTE = "authenticatedUserId";

    @Mock
    private ApplicationService applicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(new ApplicationController(applicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addFilters(new TestAuthenticationFilter())
                .build();

    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static final String SUBMIT_BODY =
            "{\"businessId\":1,\"boothSlotIds\":[1,2],\"purpose\":\"체험 부스 운영\","
                    + "\"itemsDesc\":\"사료·간식\",\"managerName\":\"김담당\",\"managerPhone\":\"010-1234-5678\","
                    + "\"managerEmail\":\"manager@petopia.com\",\"agreedTerms\":true}";

    // GET /api/fairs/{fairId}/booth-slots - 정상 조회 (인증 불필요)
    @Test
    void getsBoothSlots() throws Exception {

        given(applicationService.getBoothSlots(1L)).willReturn(
                List.of(BoothSlotLockStatusResponse.builder().boothSlotsId(1L).slotNumber("A-01").locked(false).build()));

        mockMvc.perform(get("/api/fairs/1/booth-slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slotNumber").value("A-01"));

    }

    // POST /api/fairs/{fairId}/applications - 정상 제출 (201)
    @Test
    void submitsApplication() throws Exception {

        given(applicationService.submitApplication(eq(1L), eq(1L), any())).willReturn(
                ApplicationResponse.builder().applicationId(1L).fairId(1L).status("PENDING_REVIEW").build());

        mockMvc.perform(post("/api/fairs/1/applications")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUBMIT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"));

    }

    // POST /api/fairs/{fairId}/applications - purpose 빈 값 -> @NotBlank가 400으로 막는지 확인
    @Test
    void returns400WhenPurposeBlank() throws Exception {

        mockMvc.perform(post("/api/fairs/1/applications")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessId\":1,\"boothSlotIds\":[1],\"purpose\":\"\","
                                + "\"itemsDesc\":\"사료\",\"managerName\":\"김담당\",\"managerPhone\":\"010-1234-5678\","
                                + "\"managerEmail\":\"manager@petopia.com\"}"))
                .andExpect(status().isBadRequest());

    }

    // GET /api/applications - 내 신청 현황 목록 조회
    @Test
    void getsMyApplications() throws Exception {

        given(applicationService.getMyApplications(eq(1L), isNull())).willReturn(
                List.of(ApplicationSummaryResponse.builder().applicationId(1L).fairName("가을 펫페어").status("CONFIRMED").build()));

        mockMvc.perform(get("/api/applications")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fairName").value("가을 펫페어"));

    }

    // GET /api/applications/{applicationId} - 정상 상세 조회
    @Test
    void getsApplicationDetail() throws Exception {

        given(applicationService.getApplicationDetail(1L, 1L)).willReturn(
                ApplicationDetailResponse.builder().applicationId(1L).status("CONFIRMED").build());

        mockMvc.perform(get("/api/applications/1")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(1));

    }

    // GET /api/applications/{applicationId} - 존재하지 않거나 본인 것이 아님 -> 404 + V012
    @Test
    void returns404WhenApplicationNotFound() throws Exception {

        willThrow(new CommonException(ErrorCode.APPLICATION_NOT_FOUND))
                .given(applicationService).getApplicationDetail(1L, 999L);

        mockMvc.perform(get("/api/applications/999")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V012"));

    }

    // GET /api/fairs/{fairId}/applications - 담당 행사 신청 목록 조회 (행사 담당자용)
    @Test
    void getsApplicationsForFair() throws Exception {

        given(applicationService.getApplicationsForFair(eq(1L), eq(1L), isNull())).willReturn(
                List.of(ApplicationReviewSummaryResponse.builder().applicationId(1L).businessName("멍냥사료").status("PENDING_REVIEW").build()));

        mockMvc.perform(get("/api/fairs/1/applications")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].businessName").value("멍냥사료"));

    }

    // GET /api/fairs/{fairId}/applications - 담당 행사 관리자 아님 -> 403 + V014
    @Test
    void returns403WhenNotFairAdminOnList() throws Exception {

        willThrow(new CommonException(ErrorCode.APPLICATION_ACCESS_DENIED))
                .given(applicationService).getApplicationsForFair(eq(2L), eq(1L), isNull());

        mockMvc.perform(get("/api/fairs/1/applications")
                        .with(authenticatedAs(2L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V014"));

    }

    // PUT /api/applications/{applicationId}/approve - 정상 승인 (본문 없이도 허용)
    @Test
    void approvesApplication() throws Exception {

        given(applicationService.approveApplication(eq(1L), eq(1L), any())).willReturn(
                ApplicationReviewResultResponse.builder().applicationId(1L).status("PAYMENT_PENDING").finalPrice(100000L).build());

        mockMvc.perform(put("/api/applications/1/approve")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"));

    }

    // PUT /api/applications/{applicationId}/approve - 심사 대기 상태가 아님 -> 409 + V015
    @Test
    void returns409WhenNotPendingReview() throws Exception {

        willThrow(new CommonException(ErrorCode.APPLICATION_NOT_PENDING_REVIEW))
                .given(applicationService).approveApplication(eq(1L), eq(1L), any());

        mockMvc.perform(put("/api/applications/1/approve")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("V015"));

    }

    // PUT /api/applications/{applicationId}/reject - 정상 반려
    @Test
    void rejectsApplication() throws Exception {

        given(applicationService.rejectApplication(eq(1L), eq(1L), any())).willReturn(
                ApplicationReviewResultResponse.builder().applicationId(1L).status("REJECTED").rejectReason("서류 미비").build());

        mockMvc.perform(put("/api/applications/1/reject")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"서류 미비\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

    }

    // PUT /api/applications/{applicationId}/reject - 반려 사유 없음 -> 400 + V016 (서비스단 검증)
    @Test
    void returns400WhenRejectReasonMissing() throws Exception {

        willThrow(new CommonException(ErrorCode.APPLICATION_REJECT_REASON_REQUIRED))
                .given(applicationService).rejectApplication(eq(1L), eq(1L), any());

        mockMvc.perform(put("/api/applications/1/reject")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V016"));

    }

    // GET /api/fairs/{fairId}/cancel-requests - 담당 행사 취소 요청 목록 조회
    @Test
    void getsCancelRequestsForFair() throws Exception {

        given(applicationService.getCancelRequestsForFair(eq(1L), eq(1L), isNull())).willReturn(
                List.of(ApplicationCancelRequestSummaryResponse.builder().cancelRequestId(1L).applicationId(1L).status("REQUESTED").build()));

        mockMvc.perform(get("/api/fairs/1/cancel-requests")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("REQUESTED"));

    }

    // POST /api/applications/{applicationId}/cancel-requests - 정상 제출 (201)
    @Test
    void submitsCancelRequest() throws Exception {

        mockMvc.perform(post("/api/applications/1/cancel-requests")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"일정 변경으로 인한 취소\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

    }

    // POST /api/applications/{applicationId}/cancel-requests - 이미 처리 대기 중인 취소 요청 존재 -> 409 + V018
    @Test
    void returns409WhenCancelRequestDuplicate() throws Exception {

        willThrow(new CommonException(ErrorCode.APPLICATION_CANCEL_REQUEST_DUPLICATE))
                .given(applicationService).submitCancelRequest(eq(1L), eq(1L), any());

        mockMvc.perform(post("/api/applications/1/cancel-requests")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"일정 변경으로 인한 취소\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("V018"));

    }

    // PUT /api/applications/{applicationId}/cancel-requests/approve - 정상 승인
    @Test
    void approvesCancelRequest() throws Exception {

        given(applicationService.approveCancelRequest(1L, 1L)).willReturn(
                ApplicationCancelRequestResultResponse.builder()
                        .cancelRequestId(1L).applicationId(1L).status("APPROVED")
                        .applicationStatus("CANCELED").boothDeleted(true).build());

        mockMvc.perform(put("/api/applications/1/cancel-requests/approve")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boothDeleted").value(true));

    }

    // PUT /api/applications/{applicationId}/cancel-requests/approve - 처리 대기 중인 취소 요청 없음 -> 404 + V020
    @Test
    void returns404WhenCancelRequestNotFound() throws Exception {

        willThrow(new CommonException(ErrorCode.APPLICATION_CANCEL_REQUEST_NOT_FOUND))
                .given(applicationService).approveCancelRequest(1L, 999L);

        mockMvc.perform(put("/api/applications/999/cancel-requests/approve")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V020"));

    }

    // PUT /api/applications/{applicationId}/cancel-requests/reject - 정상 반려
    @Test
    void rejectsCancelRequest() throws Exception {

        given(applicationService.rejectCancelRequest(1L, 1L)).willReturn(
                ApplicationCancelRequestResultResponse.builder()
                        .cancelRequestId(1L).applicationId(1L).status("REJECTED")
                        .applicationStatus("CONFIRMED").boothDeleted(false).build());

        mockMvc.perform(put("/api/applications/1/cancel-requests/reject")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boothDeleted").value(false));

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
