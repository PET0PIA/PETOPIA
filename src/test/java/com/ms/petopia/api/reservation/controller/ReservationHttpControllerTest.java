package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.CreateReservationResponse;
import com.ms.petopia.api.reservation.service.ReservationService;
import com.ms.petopia.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReservationHttpControllerTest {

    @Mock
    private ReservationService reservationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReservationController(reservationService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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

        verify(reservationService).create(any(), any(), any());
    }
}
