package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.CreateOnsiteReservationRequest;
import com.ms.petopia.api.reservation.dto.CreateOnsiteReservationResponse;
import com.ms.petopia.api.reservation.dto.CreateReservationRequest;
import com.ms.petopia.api.reservation.dto.CreateReservationResponse;
import com.ms.petopia.api.reservation.dto.CancelReservationResponse;
import com.ms.petopia.api.reservation.dto.GateScanResponse;
import com.ms.petopia.api.reservation.dto.OnsiteSalesPolicyResponse;
import com.ms.petopia.api.reservation.dto.ReservationPaymentCompletionResponse;
import com.ms.petopia.api.reservation.dto.ReservationPaymentContextResponse;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityDateResponse;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityResponse;
import com.ms.petopia.api.reservation.dto.ReservationListItemResponse;
import com.ms.petopia.api.reservation.dto.ReservationListResponse;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateRequest;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateResponse;
import com.ms.petopia.api.reservation.service.EntryQrService;
import com.ms.petopia.api.reservation.service.GateEntryService;
import com.ms.petopia.api.reservation.service.OnsiteReservationService;
import com.ms.petopia.api.reservation.service.OnsiteSalesPolicyService;
import com.ms.petopia.api.reservation.service.ReservationPaymentCompletionService;
import com.ms.petopia.api.reservation.service.ReservationPaymentContextService;
import com.ms.petopia.api.reservation.service.ReservationAvailabilityService;
import com.ms.petopia.api.reservation.service.ReservationCancellationService;
import com.ms.petopia.api.reservation.service.ReservationQueryService;
import com.ms.petopia.api.reservation.service.ReservationService;
import com.ms.petopia.api.reservation.service.ReservationVisitDateChangeService;
import com.ms.petopia.global.exception.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationHttpControllerTest {

    private static final String AUTHENTICATED_USER_ID_ATTRIBUTE = "authenticatedUserId";

    @Mock
    private ReservationService reservationService;
    @Mock
    private ReservationQueryService reservationQueryService;
    @Mock
    private ReservationAvailabilityService reservationAvailabilityService;
    @Mock
    private ReservationVisitDateChangeService visitDateChangeService;
    @Mock
    private ReservationCancellationService cancellationService;
    @Mock
    private OnsiteReservationService onsiteReservationService;
    @Mock
    private EntryQrService entryQrService;
    @Mock
    private OnsiteSalesPolicyService policyService;
    @Mock
    private GateEntryService gateEntryService;
    @Mock
    private ReservationPaymentContextService paymentContextService;
    @Mock
    private ReservationPaymentCompletionService paymentCompletionService;

    private MockMvc mockMvc;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReservationController(
                                reservationService,
                                onsiteReservationService,
                                entryQrService,
                                reservationQueryService,
                                reservationAvailabilityService,
                                visitDateChangeService,
                                cancellationService
                        ),
                        new OnsiteSalesAdminController(policyService),
                        new GateEntryController(gateEntryService),
                        new ReservationPaymentContractController(
                                paymentContextService,
                                paymentCompletionService
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addFilters(new TestAuthenticationFilter())
                .build();
    }

    @Test
    void changesConfirmedReservationsVisitDate() throws Exception {
        given(visitDateChangeService.changeVisitDate(any(), any(), any())).willReturn(
                new UpdateReservationVisitDateResponse(
                        30L,
                        LocalDate.of(2026, 8, 2),
                        LocalDate.of(2026, 8, 3),
                        LocalTime.of(10, 0),
                        LocalTime.of(18, 0),
                        "CONFIRMED"
                )
        );

        mockMvc.perform(patch("/api/v1/reservations/30/visit-date")
                        .with(authenticatedAs(20L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitDate\":\"2026-08-03\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousVisitDate").value("2026-08-02"))
                .andExpect(jsonPath("$.visitDate").value("2026-08-03"))
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));

        ArgumentCaptor<UpdateReservationVisitDateRequest> captor =
                ArgumentCaptor.forClass(UpdateReservationVisitDateRequest.class);
        verify(visitDateChangeService).changeVisitDate(eq(30L), eq(20L), captor.capture());
        // petIds를 안 보내면 null이다 - 서비스가 "기존 동반 정보를 그대로 둔다"로 읽는다.
        assertThat(captor.getValue().petIds()).isNull();
    }

    @Test
    void changesVisitDateWithReplacedPets() throws Exception {
        given(visitDateChangeService.changeVisitDate(any(), any(), any())).willReturn(
                new UpdateReservationVisitDateResponse(
                        30L,
                        LocalDate.of(2026, 8, 2),
                        LocalDate.of(2026, 8, 3),
                        LocalTime.of(10, 0),
                        LocalTime.of(18, 0),
                        "CONFIRMED"
                )
        );

        mockMvc.perform(patch("/api/v1/reservations/30/visit-date")
                        .with(authenticatedAs(20L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitDate\":\"2026-08-03\",\"petIds\":[7]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateReservationVisitDateRequest> captor =
                ArgumentCaptor.forClass(UpdateReservationVisitDateRequest.class);
        verify(visitDateChangeService).changeVisitDate(eq(30L), eq(20L), captor.capture());
        assertThat(captor.getValue().petIds()).containsExactly(7L);
    }

    @Test
    void changesVisitDateWithEmptyPetIdsToDropCompanions() throws Exception {
        given(visitDateChangeService.changeVisitDate(any(), any(), any())).willReturn(
                new UpdateReservationVisitDateResponse(
                        30L,
                        LocalDate.of(2026, 8, 2),
                        LocalDate.of(2026, 8, 2),
                        LocalTime.of(10, 0),
                        LocalTime.of(18, 0),
                        "CONFIRMED"
                )
        );

        // 빈 배열은 "동반 해제"라는 뜻이라 생략(null)과 구분돼야 한다.
        mockMvc.perform(patch("/api/v1/reservations/30/visit-date")
                        .with(authenticatedAs(20L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitDate\":\"2026-08-02\",\"petIds\":[]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateReservationVisitDateRequest> captor =
                ArgumentCaptor.forClass(UpdateReservationVisitDateRequest.class);
        verify(visitDateChangeService).changeVisitDate(eq(30L), eq(20L), captor.capture());
        assertThat(captor.getValue().petIds()).isEmpty();
    }

    @Test
    void createsOnsiteReservationWithPets() throws Exception {
        given(onsiteReservationService.create(any(), any(), any())).willReturn(
                new CreateOnsiteReservationResponse(
                        30L, "R20260801ONSITE1", "ONSITE_DIRECT",
                        LocalDate.of(2026, 8, 1), "CONFIRMED",
                        0, false, null, "qr-token"
                )
        );

        mockMvc.perform(post("/api/v1/fairs/10/onsite-reservations")
                        .with(authenticatedAs(20L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"petIds\":[7,9]}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateOnsiteReservationRequest> captor =
                ArgumentCaptor.forClass(CreateOnsiteReservationRequest.class);
        verify(onsiteReservationService).create(eq(10L), eq(20L), captor.capture());
        assertThat(captor.getValue().petIds()).containsExactly(7L, 9L);
    }

    @Test
    void cancelsReservationUsingAuthenticatedUser() throws Exception {
        given(cancellationService.cancel(any(), any(), any())).willReturn(
                new CancelReservationResponse(
                        30L, "CANCELED", LocalDateTime.of(2026, 8, 1, 9, 0), false, null, null, null
                )
        );

        mockMvc.perform(patch("/api/v1/reservations/30/cancel")
                        .with(authenticatedAs(20L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"일정 변경\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("CANCELED"))
                .andExpect(jsonPath("$.refunded").value(false));

        verify(cancellationService).cancel(eq(30L), eq(20L), any());
    }

    @Test
    void exposesRefundResultWhenPaidReservationIsCanceled() throws Exception {
        given(cancellationService.cancel(any(), any(), any())).willReturn(
                new CancelReservationResponse(
                        30L, "CANCELED", LocalDateTime.of(2026, 8, 1, 9, 0), true, 77L, 10_000L, "COMPLETED"
                )
        );

        mockMvc.perform(patch("/api/v1/reservations/30/cancel")
                        .with(authenticatedAs(20L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"일정 변경\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("CANCELED"))
                .andExpect(jsonPath("$.refunded").value(true))
                .andExpect(jsonPath("$.refundId").value(77))
                .andExpect(jsonPath("$.refundAmount").value(10000))
                .andExpect(jsonPath("$.refundStatus").value("COMPLETED"));
    }

    @Test
    void getsReservationAvailabilityForBookingScreen() throws Exception {
        given(reservationAvailabilityService.getAvailability(10L, null)).willReturn(
                new ReservationAvailabilityResponse(
                        10L,
                        10_000,
                        true,
                        List.of(new ReservationAvailabilityDateResponse(
                                LocalDate.of(2026, 8, 2), LocalTime.of(10, 0), LocalTime.of(18, 0), 35, true,
                                null, null
                        ))
                )
        );

        mockMvc.perform(get("/api/v1/fairs/10/reservation-availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.petAllowed").value(true))
                .andExpect(jsonPath("$.reservationFee").value(10_000))
                .andExpect(jsonPath("$.dates[0].remainingCapacity").value(35))
                .andExpect(jsonPath("$.dates[0].available").value(true))
                .andExpect(jsonPath("$.dates[0].myReservationId").doesNotExist());

        verify(reservationAvailabilityService).getAvailability(10L, null);
    }

    @Test
    void passesLoggedInUserToReservationAvailabilitySoAlreadyReservedDatesAreMarked() throws Exception {
        given(reservationAvailabilityService.getAvailability(10L, 20L)).willReturn(
                new ReservationAvailabilityResponse(
                        10L,
                        10_000,
                        true,
                        List.of(new ReservationAvailabilityDateResponse(
                                LocalDate.of(2026, 8, 2), LocalTime.of(10, 0), LocalTime.of(18, 0), 35, true,
                                77L, "PENDING_PAYMENT"
                        ))
                )
        );

        mockMvc.perform(get("/api/v1/fairs/10/reservation-availability")
                        .with(authenticatedAs(20L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dates[0].myReservationId").value(77))
                .andExpect(jsonPath("$.dates[0].myReservationStatus").value("PENDING_PAYMENT"));

        verify(reservationAvailabilityService).getAvailability(10L, 20L);
    }

    @Test
    void getsCurrentUsersReservations() throws Exception {
        given(reservationQueryService.getMyReservations(20L, 1, 10)).willReturn(
                new ReservationListResponse(List.of(
                        new ReservationListItemResponse(
                                30L, "서울 펫페어", null,
                                LocalDate.of(2026, 8, 2), LocalTime.of(10, 0), LocalTime.of(18, 0),
                                "CONFIRMED", false, true, false, null, 10_000,
                                LocalDateTime.of(2026, 8, 1, 9, 0), null, null, false
                        ),
                        // 환불까지 끝난 취소 건. 목록 카드가 "취소됨" 배지만으로는 결제 전 취소와
                        // 구분할 수 없어서 refundStatus를 함께 내려준다(금액줄 표시가 갈린다).
                        new ReservationListItemResponse(
                                31L, "부산 펫페어", null,
                                LocalDate.of(2026, 8, 3), LocalTime.of(10, 0), LocalTime.of(18, 0),
                                "CANCELED", false, false, false, null, 10_000,
                                LocalDateTime.of(2026, 8, 1, 9, 30), null, "COMPLETED", false
                        )
                ), 1, 10, 11, 2, false)
        );

        mockMvc.perform(get("/api/v1/reservations/me?page=1&size=10")
                        .with(authenticatedAs(20L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].fairName").value("서울 펫페어"))
                .andExpect(jsonPath("$.items[0].entryStartTime").value("10:00:00"))
                .andExpect(jsonPath("$.items[0].isEnded").value(false))
                .andExpect(jsonPath("$.items[0].qrAvailable").value(true))
                // 환불이 없는 예약은 refundStatus가 null로 내려가야 한다(응답에서 빠지면 안 된다).
                .andExpect(jsonPath("$.items[0].refundStatus").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[1].reservationStatus").value("CANCELED"))
                .andExpect(jsonPath("$.items[1].refundStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(reservationQueryService).getMyReservations(20L, 1, 10);
    }

    @Test
    void createsAdvanceReservationUsingAuthenticatedUser() throws Exception {
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 1, 10, 10);
        given(reservationService.create(any(), any(), any())).willReturn(
                new CreateReservationResponse(
                        30L, "R20260801ADVANCE1", "ADVANCE", "PENDING_PAYMENT",
                        10_000, true, deadline, null
                )
        );

        mockMvc.perform(post("/api/v1/fairs/10/reservations")
                        .with(authenticatedAs(20L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "visitDate": "2026-08-02",
                                  "reservationTermsAgreed": true,
                                  "reservationTermsVersion": "advance-v1",
                                  "petIds": [7, 9]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationType").value("ADVANCE"));

        ArgumentCaptor<CreateReservationRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateReservationRequest.class);
        verify(reservationService).create(eq(10L), eq(20L), requestCaptor.capture());

        CreateReservationRequest captured = requestCaptor.getValue();
        assertThat(captured.visitDate()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(captured.reservationTermsAgreed()).isTrue();
        assertThat(captured.reservationTermsVersion()).isEqualTo("advance-v1");
        // record에 호환 생성자를 하나 더 뒀는데도 Jackson이 정식(canonical) 생성자로 바인딩하는지 확인한다.
        assertThat(captured.petIds()).containsExactly(7L, 9L);
    }

    @Test
    void createsAdvanceReservationWithoutPetIdsWhenFieldIsAbsent() throws Exception {
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 1, 10, 10);
        given(reservationService.create(any(), any(), any())).willReturn(
                new CreateReservationResponse(
                        30L, "R20260801ADVANCE1", "ADVANCE", "PENDING_PAYMENT",
                        10_000, true, deadline, null
                )
        );

        mockMvc.perform(post("/api/v1/fairs/10/reservations")
                        .with(authenticatedAs(20L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "visitDate": "2026-08-02",
                                  "reservationTermsAgreed": true,
                                  "reservationTermsVersion": "advance-v1"
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateReservationRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateReservationRequest.class);
        verify(reservationService).create(eq(10L), eq(20L), requestCaptor.capture());
        // 필드를 아예 안 보내면 null이다. 동반 없음과 같게 취급되고(P3), 서비스가 그렇게 처리한다.
        assertThat(requestCaptor.getValue().petIds()).isNull();
    }

    @Test
    void createsOnsiteReservationUsingAuthenticatedUser() throws Exception {
        given(onsiteReservationService.create(any(), any(), any())).willReturn(
                new CreateOnsiteReservationResponse(
                        30L, "R20260801ONSITE1", "ONSITE_DIRECT",
                        LocalDate.of(2026, 8, 1), "CONFIRMED",
                        0, false, null, "qr-token"
                )
        );

        mockMvc.perform(post("/api/v1/fairs/10/onsite-reservations")
                        .with(authenticatedAs(20L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationType").value("ONSITE_DIRECT"))
                .andExpect(jsonPath("$.entryQrToken").value("qr-token"));

        // 무료 현장예매는 본문 없이 호출하므로 request는 null로 전달된다.
        verify(onsiteReservationService).create(eq(10L), eq(20L), isNull());
    }

    @Test
    void ignoresUserIdHeaderAndUsesAuthenticationPrincipal() throws Exception {
        given(reservationService.create(any(), any(), any())).willReturn(
                new CreateReservationResponse(
                        30L, "R20260801ADVANCE1", "ADVANCE", "PENDING_PAYMENT",
                        10_000, true, LocalDateTime.of(2026, 8, 1, 10, 10), null
                )
        );

        mockMvc.perform(post("/api/v1/fairs/10/reservations")
                        .with(authenticatedAs(20L))
                        .header(TemporaryAuthHeaders.USER_ID, 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visitDate": "2026-08-02"}
                                """))
                .andExpect(status().isCreated());

        verify(reservationService).create(eq(10L), eq(20L), any());
    }

    @Test
    void getsOnlyCurrentUsersEntryQr() throws Exception {
        given(entryQrService.issueForUser(30L, 20L)).willReturn("qr-token");

        mockMvc.perform(get("/api/v1/reservations/30/entry-qr")
                        .with(authenticatedAs(20L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrToken").value("qr-token"));

        verify(entryQrService).issueForUser(30L, 20L);
    }

    @Test
    void savesOnsitePolicyUsingAuthenticatedAdmin() throws Exception {
        given(policyService.save(any(), any(), any(), any())).willReturn(
                new OnsiteSalesPolicyResponse(
                        10L, 11L, LocalDate.of(2026, 8, 1),
                        12_000, 50, 7, "OPEN", 0, LocalDateTime.of(2026, 8, 1, 9, 0)
                )
        );

        mockMvc.perform(put("/api/v1/admin/fairs/10/dates/11/onsite-sales-policy")
                        .with(authenticatedAs(20L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"price":12000,"capacity":50,"status":"OPEN","expectedVersion":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void getsOnsitePolicyUsingAuthenticatedAdmin() throws Exception {
        given(policyService.get(10L, 11L, 20L)).willReturn(
                new OnsiteSalesPolicyResponse(
                        10L, 11L, LocalDate.of(2026, 8, 1),
                        12_000, 50, 7, "OPEN", 3, LocalDateTime.of(2026, 8, 1, 9, 0)
                )
        );

        mockMvc.perform(get("/api/v1/admin/fairs/10/dates/11/onsite-sales-policy")
                        .with(authenticatedAs(20L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(12_000))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void scansGateQrUsingAuthenticatedAdmin() throws Exception {
        given(gateEntryService.scan(any(), any(), any(), any())).willReturn(
                new GateScanResponse(
                        "FIRST_ENTRY", true, "ONSITE_DIRECT",
                        LocalDateTime.of(2026, 8, 1, 10, 0)
                )
        );

        mockMvc.perform(post("/api/v1/admin/fairs/10/gate-entries/scan")
                        .with(authenticatedAs(20L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qrToken":"qr-token","deviceInfo":"tablet"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("FIRST_ENTRY"));
    }

    @Test
    void exposesPaymentContextOnlyToTemporaryPaymentCaller() throws Exception {
        given(paymentContextService.getPayableContext(30L)).willReturn(
                new ReservationPaymentContextResponse(
                        30L, 10L, 20L, "ONSITE_DIRECT", 12_000,
                        LocalDateTime.of(2026, 8, 1, 10, 10)
                )
        );

        mockMvc.perform(get("/internal/api/v1/reservations/30/payment-context")
                        .header(TemporaryAuthHeaders.INTERNAL_CALLER, TemporaryAuthHeaders.PAYMENT_CALLER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(12000));
    }

    @Test
    void acceptsPaymentCompletionFromTemporaryPaymentCaller() throws Exception {
        given(paymentCompletionService.complete(any())).willReturn(
                new ReservationPaymentCompletionResponse(30L, "CONFIRMED", false, "qr-token")
        );

        mockMvc.perform(post("/internal/api/v1/reservation-payment-completions")
                        .header(TemporaryAuthHeaders.INTERNAL_CALLER, TemporaryAuthHeaders.PAYMENT_CALLER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId":"event-1",
                                  "paymentId":40,
                                  "reservationId":30,
                                  "paidAmount":12000,
                                  "paidAt":"2026-08-01T10:05:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));
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
