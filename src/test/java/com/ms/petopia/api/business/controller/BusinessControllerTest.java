package com.ms.petopia.api.business.controller;

import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.dto.response.BusinessResponse;
import com.ms.petopia.api.business.dto.response.BusinessReviewDetailResponse;
import com.ms.petopia.api.business.dto.response.BusinessReviewResultResponse;
import com.ms.petopia.api.business.dto.response.BusinessReviewSummaryResponse;
import com.ms.petopia.api.business.service.BusinessService;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * BusinessController 통합 테스트. BusinessService는 Mock으로 대체하고,
 * standaloneSetup + GlobalExceptionHandler로 실제 라우팅/@Valid 검증/
 * 예외→HTTP 상태코드 매핑까지 확인한다. @AuthenticationPrincipal은 예약 도메인
 * (ReservationHttpControllerTest)과 동일한 가짜 인증 필터 패턴으로 시뮬레이션한다.
 */
@ExtendWith(MockitoExtension.class)
class BusinessControllerTest {

    private static final String AUTHENTICATED_USER_ID_ATTRIBUTE = "authenticatedUserId";

    @Mock
    private BusinessService businessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(new BusinessController(businessService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addFilters(new TestAuthenticationFilter())
                .build();

    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // POST /api/businesses - 정상 등록 (201)
    @Test
    void registersBusiness() throws Exception {

        given(businessService.registerBusiness(eq(1L), any())).willReturn(
                BusinessResponse.builder()
                        .businessId(1L)
                        .name("멍냥사료")
                        .verifyStatus("VERIFIED")
                        .build());

        mockMvc.perform(post("/api/businesses")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"멍냥사료\",\"ceoName\":\"김대표\",\"bizRegNo\":\"1234567890\","
                                + "\"startDate\":\"2020-01-01\",\"address\":\"서울시\","
                                + "\"businessRegDocKey\":\"uploads/document/reg.pdf\",\"phone\":\"02-1234-5678\","
                                + "\"agreedTerms\":true,\"agreedPrivacy\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("멍냥사료"))
                .andExpect(jsonPath("$.data.verifyStatus").value("VERIFIED"));

    }

    // POST /api/businesses - bizRegNo 형식 위반(숫자 10자리 아님) -> @Pattern이 400으로 막는지 확인
    @Test
    void returns400WhenBizRegNoInvalidFormat() throws Exception {

        mockMvc.perform(post("/api/businesses")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"멍냥사료\",\"ceoName\":\"김대표\",\"bizRegNo\":\"abc\","
                                + "\"startDate\":\"2020-01-01\",\"address\":\"서울시\","
                                + "\"businessRegDocKey\":\"uploads/document/reg.pdf\",\"phone\":\"02-1234-5678\"}"
                                + "\"agreedTerms\":true,\"agreedPrivacy\":true}"))
                .andExpect(status().isBadRequest());

    }

    // POST /api/businesses - 국세청 진위확인 실패 -> 400 + V006
    @Test
    void returns400WhenVerificationFailed() throws Exception {

        willThrow(new CommonException(ErrorCode.BUSINESS_VERIFICATION_FAILED))
                .given(businessService).registerBusiness(eq(1L), any());

        mockMvc.perform(post("/api/businesses")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"멍냥사료\",\"ceoName\":\"김대표\",\"bizRegNo\":\"1234567890\","
                                + "\"startDate\":\"2020-01-01\",\"address\":\"서울시\","
                                + "\"businessRegDocKey\":\"uploads/document/reg.pdf\",\"phone\":\"02-1234-5678\","
                                + "\"agreedTerms\":true,\"agreedPrivacy\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V006"));

    }

    // GET /api/businesses - 내 사업자 목록 조회
    @Test
    void getsMyBusinesses() throws Exception {

        given(businessService.getMyBusinesses(1L)).willReturn(
                List.of(BusinessResponse.builder().businessId(1L).name("멍냥사료").build()));

        mockMvc.perform(get("/api/businesses")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("멍냥사료"));

    }

    // GET /api/businesses/{businessId} - 정상 상세 조회
    @Test
    void getsBusinessDetail() throws Exception {

        given(businessService.getBusiness(1L, 1L)).willReturn(
                BusinessResponse.builder()
                        .businessId(1L)
                        .name("멍냥사료")
                        .startDate(LocalDate.of(2020, 1, 1))
                        .verifyStatus("VERIFIED")
                        .build());

        mockMvc.perform(get("/api/businesses/1")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessId").value(1));

    }

    // GET /api/businesses/{businessId} - 존재하지 않는 사업자 -> 404 + V001
    @Test
    void returns404WhenBusinessNotFound() throws Exception {

        willThrow(new CommonException(ErrorCode.BUSINESS_NOT_FOUND))
                .given(businessService).getBusiness(1L, 999L);

        mockMvc.perform(get("/api/businesses/999")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V001"));

    }

    // GET /api/businesses/{businessId} - 본인 소유 아님 -> 403 + A002
    @Test
    void returns403WhenNotBusinessOwner() throws Exception {

        willThrow(new CommonException(ErrorCode.ACCESS_DENIED, "본인 소유의 사업자만 조회할 수 있습니다."))
                .given(businessService).getBusiness(2L, 1L);

        mockMvc.perform(get("/api/businesses/1")
                        .with(authenticatedAs(2L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));

    }

    // GET /api/businesses/review - 심사 목록 조회 (기본값 PENDING_REVIEW)
    @Test
    void getsBusinessesForReview() throws Exception {

        given(businessService.getBusinessesForReview(Business.ApprovalStatus.PENDING_REVIEW)).willReturn(
                List.of(new BusinessReviewSummaryResponse(1L, "멍냥사료", "김대표", "1234567890", "VERIFIED", null)));

        mockMvc.perform(get("/api/businesses/review")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].businessId").value(1));

    }

    // GET /api/businesses/{businessId}/review - 심사 상세 조회
    @Test
    void getsBusinessReviewDetail() throws Exception {

        given(businessService.getBusinessReviewDetail(1L)).willReturn(
                BusinessReviewDetailResponse.builder()
                        .businessId(1L)
                        .name("멍냥사료")
                        .approvalStatus("PENDING_REVIEW")
                        .build());

        mockMvc.perform(get("/api/businesses/1/review")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessId").value(1))
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING_REVIEW"));

    }

    // PATCH /api/businesses/{businessId}/approve - 정상 승인
    @Test
    void approvesBusiness() throws Exception {

        given(businessService.approveBusiness(1L, 1L)).willReturn(
                BusinessReviewResultResponse.builder().businessId(1L).approvalStatus("APPROVED").build());

        mockMvc.perform(patch("/api/businesses/1/approve")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));

    }

    // PATCH /api/businesses/{businessId}/approve - 이미 처리된 사업자 -> 409 + V027
    @Test
    void returns409WhenApproveNotPendingReview() throws Exception {

        willThrow(new CommonException(ErrorCode.BUSINESS_NOT_PENDING_REVIEW))
                .given(businessService).approveBusiness(1L, 1L);

        mockMvc.perform(patch("/api/businesses/1/approve")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("V027"));

    }

    // PATCH /api/businesses/{businessId}/reject - 정상 반려
    @Test
    void rejectsBusiness() throws Exception {

        given(businessService.rejectBusiness(eq(1L), eq(1L), any())).willReturn(
                BusinessReviewResultResponse.builder()
                        .businessId(1L).approvalStatus("REJECTED").rejectReason("서류 불일치").build());

        mockMvc.perform(patch("/api/businesses/1/reject")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"서류 불일치\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("REJECTED"));

    }

    // PATCH /api/businesses/{businessId}/reject - 반려 사유 누락 -> 400 + V028
    @Test
    void returns400WhenRejectReasonMissing() throws Exception {

        willThrow(new CommonException(ErrorCode.BUSINESS_REJECT_REASON_REQUIRED))
                .given(businessService).rejectBusiness(eq(1L), eq(1L), any());

        mockMvc.perform(patch("/api/businesses/1/reject")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V028"));

    }

    // PATCH /api/businesses/{businessId}/revoke - 정상 취소
    @Test
    void revokesBusiness() throws Exception {

        given(businessService.revokeBusiness(eq(1L), eq(1L), any())).willReturn(
                BusinessReviewResultResponse.builder()
                        .businessId(1L).approvalStatus("REVOKED").rejectReason("조작 서류 발각").build());

        mockMvc.perform(patch("/api/businesses/1/revoke")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revokeReason\":\"조작 서류 발각\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalStatus").value("REVOKED"));

    }

    // PATCH /api/businesses/{businessId}/revoke - 승인 상태가 아님 -> 409 + V029
    @Test
    void returns409WhenRevokeNotApproved() throws Exception {

        willThrow(new CommonException(ErrorCode.BUSINESS_NOT_APPROVED))
                .given(businessService).revokeBusiness(eq(1L), eq(1L), any());

        mockMvc.perform(patch("/api/businesses/1/revoke")
                        .with(authenticatedAs(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revokeReason\":\"조작 서류 발각\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("V029"));

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
