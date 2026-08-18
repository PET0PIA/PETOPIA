package com.ms.petopia.api.settlement.controller;

import com.ms.petopia.api.settlement.dto.SettlementResponse;
import com.ms.petopia.api.settlement.service.SettlementExportService;
import com.ms.petopia.api.settlement.service.SettlementService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SettlementControllerTest {

    @Mock
    private SettlementService settlementService;

    @Mock
    private SettlementExportService settlementExportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SettlementController(settlementService, settlementExportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private SettlementResponse sampleResponse(String status) {
        return new SettlementResponse(
                1L, 10L, 20L, 150000L, 0L,
                new BigDecimal("0.0500"), 7500L, 142500L,
                status, null, null
        );
    }

    @Test
    void calculatesSettlement() throws Exception {
        given(settlementService.calculate(10L, 20L)).willReturn(sampleResponse("PENDING"));

        mockMvc.perform(post("/api/fairs/10/vendors/20/settlements"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.netAmount").value(142500));
    }

    @Test
    void returns409WhenSettlementAlreadyExists() throws Exception {
        willThrow(new CommonException(ErrorCode.SETTLEMENT_ALREADY_EXISTS))
                .given(settlementService).calculate(10L, 20L);

        mockMvc.perform(post("/api/fairs/10/vendors/20/settlements"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ST002"));
    }

    @Test
    void confirmsSettlement() throws Exception {
        given(settlementService.confirm(eq(1L), eq(99L))).willReturn(sampleResponse("CONFIRMED"));

        mockMvc.perform(put("/api/settlements/1/confirm")
                        .header(SettlementTemporaryAuthHeaders.USER_ID, 99))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void returns404WhenConfirmingNonExistentSettlement() throws Exception {
        willThrow(new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND))
                .given(settlementService).confirm(eq(999L), eq(99L));

        mockMvc.perform(put("/api/settlements/999/confirm")
                        .header(SettlementTemporaryAuthHeaders.USER_ID, 99))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ST001"));
    }

    @Test
    void returns409WhenConfirmingAlreadyConfirmedSettlement() throws Exception {
        willThrow(new CommonException(ErrorCode.SETTLEMENT_NOT_CONFIRMABLE))
                .given(settlementService).confirm(eq(1L), eq(99L));

        mockMvc.perform(put("/api/settlements/1/confirm")
                        .header(SettlementTemporaryAuthHeaders.USER_ID, 99))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ST003"));
    }

    @Test
    void recalculatesSettlement() throws Exception {
        given(settlementService.recalculate(1L)).willReturn(sampleResponse("PENDING"));

        mockMvc.perform(put("/api/settlements/1/recalculate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossAmount").value(150000));
    }

    @Test
    void returns409WhenRecalculatingConfirmedSettlement() throws Exception {
        willThrow(new CommonException(ErrorCode.SETTLEMENT_NOT_RECALCULABLE))
                .given(settlementService).recalculate(1L);

        mockMvc.perform(put("/api/settlements/1/recalculate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ST004"));
    }

    @Test
    void returns404WhenRecalculatingNonExistentSettlement() throws Exception {
        willThrow(new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND))
                .given(settlementService).recalculate(999L);

        mockMvc.perform(put("/api/settlements/999/recalculate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ST001"));
    }

    @Test
    void getsSettlementDetail() throws Exception {
        given(settlementService.getByFairAndBusiness(10L, 20L)).willReturn(sampleResponse("PENDING"));

        mockMvc.perform(get("/api/fairs/10/vendors/20/settlement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossAmount").value(150000));
    }

    @Test
    void returns404WhenSettlementNotFound() throws Exception {
        willThrow(new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND))
                .given(settlementService).getByFairAndBusiness(10L, 30L);

        mockMvc.perform(get("/api/fairs/10/vendors/30/settlement"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getsSettlementsByFair() throws Exception {
        given(settlementService.getByFair(10L)).willReturn(List.of(sampleResponse("PENDING")));

        mockMvc.perform(get("/api/fairs/10/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void exportsSettlementsAsExcel() throws Exception {
        byte[] fakeExcelBytes = {1, 2, 3};
        given(settlementExportService.exportAsExcel(10L)).willReturn(fakeExcelBytes);

        mockMvc.perform(get("/api/fairs/10/settlements/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"settlements-10.xlsx\""))
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
}
