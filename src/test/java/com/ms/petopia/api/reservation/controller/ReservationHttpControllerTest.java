package com.ms.petopia.api.reservation.controller;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationHttpControllerTest {

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
                        .header(TemporaryAuthHeaders.USER_ID, 20)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitDate\":\"2026-08-03\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousVisitDate").value("2026-08-02"))
                .andExpect(jsonPath("$.visitDate").value("2026-08-03"))
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));

        verify(visitDateChangeService).changeVisitDate(eq(30L), eq(20L), any());
    }

    @Test
    void cancelsReservationUsingTemporaryUserHeader() throws Exception {
        given(cancellationService.cancel(any(), any(), any())).willReturn(
                new CancelReservationResponse(30L, "CANCELED", LocalDateTime.of(2026, 8, 1, 9, 0))
        );

        mockMvc.perform(patch("/api/v1/reservations/30/cancel")
                        .header(TemporaryAuthHeaders.USER_ID, 20)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"일정 변경\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("CANCELED"));

        verify(cancellationService).cancel(eq(30L), eq(20L), any());
    }

    @Test
    void getsReservationAvailabilityForBookingScreen() throws Exception {
        given(reservationAvailabilityService.getAvailability(10L)).willReturn(
                new ReservationAvailabilityResponse(
                        10L,
                        10_000,
                        List.of(new ReservationAvailabilityDateResponse(
                                LocalDate.of(2026, 8, 2), LocalTime.of(10, 0), LocalTime.of(18, 0), 35, true
                        ))
                )
        );

        mockMvc.perform(get("/api/v1/fairs/10/reservation-availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationFee").value(10_000))
                .andExpect(jsonPath("$.dates[0].remainingCapacity").value(35))
                .andExpect(jsonPath("$.dates[0].available").value(true));

        verify(reservationAvailabilityService).getAvailability(10L);
    }

    @Test
    void getsCurrentUsersReservations() throws Exception {
        given(reservationQueryService.getMyReservations(20L, 1, 10)).willReturn(
                new ReservationListResponse(List.of(
                        new ReservationListItemResponse(
                                30L, "서울 펫페어", null,
                                LocalDate.of(2026, 8, 2), LocalTime.of(10, 0), LocalTime.of(18, 0),
                                "CONFIRMED", false, true, false, 10_000,
                                LocalDateTime.of(2026, 8, 1, 9, 0), null
                        )
                ), 1, 10, 11, 2, false)
        );

        mockMvc.perform(get("/api/v1/reservations/me?page=1&size=10")
                        .header(TemporaryAuthHeaders.USER_ID, 20))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].fairName").value("서울 펫페어"))
                .andExpect(jsonPath("$.items[0].entryStartTime").value("10:00:00"))
                .andExpect(jsonPath("$.items[0].isEnded").value(false))
                .andExpect(jsonPath("$.items[0].qrAvailable").value(true))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(reservationQueryService).getMyReservations(20L, 1, 10);
    }

    @Test
    void createsAdvanceReservationUsingTemporaryUserHeader() throws Exception {
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 1, 10, 10);
        given(reservationService.create(any(), any(), any())).willReturn(
                new CreateReservationResponse(
                        30L, "R20260801ADVANCE1", "ADVANCE", "PENDING_PAYMENT",
                        10_000, true, deadline, null
                )
        );

        mockMvc.perform(post("/api/v1/fairs/10/reservations")
                        .header(TemporaryAuthHeaders.USER_ID, 20)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "visitDate": "2026-08-02",
                                  "reservationTermsAgreed": true,
                                  "reservationTermsVersion": "advance-v1"
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
    }

    @Test
    void createsOnsiteReservationUsingTemporaryUserHeader() throws Exception {
        given(onsiteReservationService.create(any(), any(), any())).willReturn(
                new CreateOnsiteReservationResponse(
                        30L, "R20260801ONSITE1", "ONSITE_DIRECT",
                        LocalDate.of(2026, 8, 1), "CONFIRMED",
                        0, false, null, "qr-token"
                )
        );

        mockMvc.perform(post("/api/v1/fairs/10/onsite-reservations")
                        .header(TemporaryAuthHeaders.USER_ID, 20))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationType").value("ONSITE_DIRECT"))
                .andExpect(jsonPath("$.entryQrToken").value("qr-token"));

        // 무료 현장예매는 본문 없이 호출하므로 request는 null로 전달된다.
        verify(onsiteReservationService).create(eq(10L), eq(20L), isNull());
    }

    @Test
    void rejectsAdvanceReservationWithoutTemporaryUserHeader() throws Exception {
        mockMvc.perform(post("/api/v1/fairs/10/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visitDate": "2026-08-02"}
                                """))
                .andExpect(status().isBadRequest());

        verify(reservationService, never()).create(any(), any(), any());
    }

    @Test
    void getsOnlyCurrentUsersEntryQr() throws Exception {
        given(entryQrService.issueForUser(30L, 20L)).willReturn("qr-token");

        mockMvc.perform(get("/api/v1/reservations/30/entry-qr")
                        .header(TemporaryAuthHeaders.USER_ID, 20))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrToken").value("qr-token"));

        verify(entryQrService).issueForUser(30L, 20L);
    }

    @Test
    void savesOnsitePolicyUsingTemporaryAdminHeader() throws Exception {
        given(policyService.save(any(), any(), any(), any())).willReturn(
                new OnsiteSalesPolicyResponse(
                        10L, 11L, LocalDate.of(2026, 8, 1),
                        12_000, "OPEN", 0, LocalDateTime.of(2026, 8, 1, 9, 0)
                )
        );

        mockMvc.perform(put("/api/v1/admin/fairs/10/dates/11/onsite-sales-policy")
                        .header(TemporaryAuthHeaders.USER_ID, 20)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"price":12000,"status":"OPEN","expectedVersion":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void getsOnsitePolicyUsingTemporaryAdminHeader() throws Exception {
        given(policyService.get(10L, 11L, 20L)).willReturn(
                new OnsiteSalesPolicyResponse(
                        10L, 11L, LocalDate.of(2026, 8, 1),
                        12_000, "OPEN", 3, LocalDateTime.of(2026, 8, 1, 9, 0)
                )
        );

        mockMvc.perform(get("/api/v1/admin/fairs/10/dates/11/onsite-sales-policy")
                        .header(TemporaryAuthHeaders.USER_ID, 20))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(12_000))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void scansGateQrUsingTemporaryAdminHeader() throws Exception {
        given(gateEntryService.scan(any(), any(), any(), any())).willReturn(
                new GateScanResponse(
                        "FIRST_ENTRY", true, "ONSITE_DIRECT",
                        LocalDateTime.of(2026, 8, 1, 10, 0)
                )
        );

        mockMvc.perform(post("/api/v1/admin/fairs/10/gate-entries/scan")
                        .header(TemporaryAuthHeaders.USER_ID, 20)
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
}
