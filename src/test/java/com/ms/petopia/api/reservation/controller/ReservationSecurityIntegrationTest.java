package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.CreateReservationResponse;
import com.ms.petopia.api.reservation.dto.OnsiteSalesPolicyResponse;
import com.ms.petopia.api.reservation.dto.ReservationListResponse;
import com.ms.petopia.api.reservation.service.EntryQrService;
import com.ms.petopia.api.reservation.service.GateEntryService;
import com.ms.petopia.api.reservation.service.OnsiteReservationService;
import com.ms.petopia.api.reservation.service.OnsiteSalesPolicyService;
import com.ms.petopia.api.reservation.service.ReservationAvailabilityService;
import com.ms.petopia.api.reservation.service.ReservationCancellationService;
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
        controllers = {ReservationController.class, OnsiteSalesAdminController.class, GateEntryController.class},
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
                        12_000, "OPEN", 3, LocalDateTime.of(2026, 8, 1, 9, 0)
                )
        );

        mockMvc.perform(get("/api/v1/admin/fairs/10/dates/11/onsite-sales-policy")
                        .header("Authorization", bearerToken(20L, "EVENT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(12_000));

        verify(policyService).get(10L, 11L, 20L);
    }

    private String bearerToken(Long userId, String role) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(userId, role);
    }
}
