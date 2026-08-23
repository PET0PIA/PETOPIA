package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.CreateReservationResponse;
import com.ms.petopia.api.reservation.dto.OnsiteSalesPolicyResponse;
import com.ms.petopia.api.reservation.dto.ReservationListResponse;
import com.ms.petopia.api.reservation.dto.ReservationPaymentContextResponse;
import com.ms.petopia.api.reservation.service.EntryQrService;
import com.ms.petopia.api.reservation.service.GateEntryService;
import com.ms.petopia.api.reservation.service.OnsiteReservationService;
import com.ms.petopia.api.reservation.service.OnsiteSalesPolicyService;
import com.ms.petopia.api.reservation.service.ReservationAvailabilityService;
import com.ms.petopia.api.reservation.service.ReservationCancellationService;
import com.ms.petopia.api.reservation.service.ReservationPaymentCompletionService;
import com.ms.petopia.api.reservation.service.ReservationPaymentContextService;
import com.ms.petopia.api.reservation.service.ReservationQueryService;
import com.ms.petopia.api.reservation.service.ReservationService;
import com.ms.petopia.api.reservation.service.ReservationVisitDateChangeService;
import com.ms.petopia.global.security.SecurityConfig;
import com.ms.petopia.global.security.jwt.JwtAuthenticationFilter;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {ReservationController.class, OnsiteSalesAdminController.class,
                GateEntryController.class, ReservationPaymentContractController.class},
        properties = {
                "jwt.secret=c2VjdXJlLXRlc3Qta2V5LXRlc3Qta2V5LXRlc3Qta2V5LXRlc3Qta2V5",
                "jwt.access-token-expiration=3600000",
                "jwt.refresh-token-expiration=1209600000"
        }
)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class ReservationSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ReservationService reservationService;
    @MockitoBean
    private OnsiteReservationService onsiteReservationService;
    @MockitoBean
    private EntryQrService entryQrService;
    @MockitoBean
    private ReservationQueryService reservationQueryService;
    @MockitoBean
    private ReservationAvailabilityService reservationAvailabilityService;
    @MockitoBean
    private ReservationVisitDateChangeService visitDateChangeService;
    @MockitoBean
    private ReservationCancellationService cancellationService;
    @MockitoBean
    private OnsiteSalesPolicyService policyService;
    @MockitoBean
    private GateEntryService gateEntryService;
    @MockitoBean
    private ReservationPaymentContextService paymentContextService;
    @MockitoBean
    private ReservationPaymentCompletionService paymentCompletionService;

    @Test
    void unauthenticatedReservationRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reservationQueryService);
    }

    @Test
    void refreshTokenCannotAccessReservationApi() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/me")
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateRefreshToken(20L)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reservationQueryService);
    }

    @Test
    void authenticatedUserIdComesFromAccessTokenNotRequestHeader() throws Exception {
        given(reservationService.create(eq(10L), eq(20L), any())).willReturn(
                new CreateReservationResponse(
                        30L, "R20260801ADVANCE1", "ADVANCE", "PENDING_PAYMENT",
                        10_000, true, LocalDateTime.of(2026, 8, 1, 10, 10), null
                )
        );

        mockMvc.perform(post("/api/v1/fairs/10/reservations")
                        .header("Authorization", bearerToken(20L, "USER"))
                        .header("X-User-Id", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "visitDate": "2026-08-02",
                                  "reservationTermsAgreed": true,
                                  "reservationTermsVersion": "advance-v1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value(30L));

        verify(reservationService).create(eq(10L), eq(20L), any());
    }

    @Test
    void normalUserCannotAccessReservationAdminApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/fairs/10/dates/11/onsite-sales-policy")
                        .header("Authorization", bearerToken(20L, "USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(policyService);
    }

    @Test
    void eventAdminCanAccessReservationAdminApi() throws Exception {
        given(policyService.get(10L, 11L, 20L)).willReturn(
                new OnsiteSalesPolicyResponse(
                        10L, 11L, LocalDate.of(2026, 8, 1),
                        12_000, 50, 7, "OPEN", 3, LocalDateTime.of(2026, 8, 1, 9, 0)
                )
        );

        mockMvc.perform(get("/api/v1/admin/fairs/10/dates/11/onsite-sales-policy")
                        .header("Authorization", bearerToken(20L, "EVENT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(12_000));

        verify(policyService).get(10L, 11L, 20L);
    }

    /**
     * 내부 계약 API(/internal/**)는 같은 컨테이너 안에서 자기 자신을 부르는 호출만 통과한다.
     * MockMvc의 기본 remoteAddr가 127.0.0.1이므로 별도 설정 없이 루프백 경로가 된다.
     */
    @Test
    void internalContractApiAllowsLoopbackCall() throws Exception {
        given(paymentContextService.getPayableContext(30L)).willReturn(
                new ReservationPaymentContextResponse(
                        30L, 10L, 20L, "ADVANCE", 10_000, LocalDateTime.of(2026, 8, 1, 10, 10)
                )
        );

        mockMvc.perform(get("/internal/api/v1/reservations/30/payment-context")
                        .header(TemporaryAuthHeaders.INTERNAL_CALLER, TemporaryAuthHeaders.PAYMENT_CALLER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payerUserId").value(20L));

        verify(paymentContextService).getPayableContext(30L);
    }

    /**
     * 루프백이 아닌 곳에서 온 요청은 X-Internal-Caller 헤더가 맞아도 막힌다.
     * 이 헤더값은 고정 문자열("PAYMENT")이라, 예전엔 이것만 붙이면 외부에서도 남의 예약
     * 결제정보를 읽을 수 있었다 - 그 구멍을 막았는지 확인하는 테스트다.
     */
    @Test
    void internalContractApiRejectsRemoteCallEvenWithCallerHeader() throws Exception {
        mockMvc.perform(get("/internal/api/v1/reservations/30/payment-context")
                        .header(TemporaryAuthHeaders.INTERNAL_CALLER, TemporaryAuthHeaders.PAYMENT_CALLER)
                        .with(remoteAddr("203.0.113.9")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentContextService);
    }

    /**
     * 로그인해도 외부에서는 못 들어온다 - 일반 회원 토큰이 내부 API 우회 경로가 되지 않는지 확인.
     * (익명은 401, 인증된 사용자는 403으로 갈린다)
     */
    @Test
    void internalContractApiRejectsRemoteCallFromLoggedInUser() throws Exception {
        mockMvc.perform(post("/internal/api/v1/reservation-payment-completions")
                        .header("Authorization", bearerToken(20L, "USER"))
                        .header(TemporaryAuthHeaders.INTERNAL_CALLER, TemporaryAuthHeaders.PAYMENT_CALLER)
                        .with(remoteAddr("203.0.113.9"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(paymentCompletionService);
    }

    private String bearerToken(Long userId, String role) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(userId, role);
    }

    /** 요청이 루프백이 아닌 곳에서 온 것처럼 remoteAddr을 바꾼다. */
    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
