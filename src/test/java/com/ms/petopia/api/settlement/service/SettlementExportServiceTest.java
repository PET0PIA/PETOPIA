package com.ms.petopia.api.settlement.service;

import com.ms.petopia.api.settlement.dto.FairRevenueSummaryResponse;
import com.ms.petopia.api.settlement.dto.SettlementResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SettlementExportServiceTest {

    @Mock
    private SettlementService settlementService;

    @InjectMocks
    private SettlementExportService settlementExportService;

    private SettlementResponse response(Long settlementId, Long businessId, String status,
                                         LocalDateTime confirmedAt, Long confirmedByUserId) {
        return new SettlementResponse(
                settlementId, 10L, businessId, 150000L, 20000L,
                new BigDecimal("0.0500"), 6500L, 123500L,
                status, confirmedAt, confirmedByUserId
        );
    }

    @Test
    @DisplayName("행사의 정산 목록을 시트 한 장짜리 엑셀로 만든다 - 헤더 + 데이터 행 수 일치")
    void exportsSettlementsAsExcel() throws Exception {
        given(settlementService.getByFair(10L)).willReturn(List.of(
                response(1L, 20L, "CONFIRMED", LocalDateTime.of(2026, 8, 13, 10, 0), 99L),
                response(2L, 21L, "PENDING", null, null)
        ));

        byte[] bytes = settlementExportService.exportAsExcel(10L);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("정산내역");
            assertThat(sheet).isNotNull();

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("정산ID");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("업체ID");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("정산액");

            Row confirmedRow = sheet.getRow(1);
            assertThat(confirmedRow.getCell(0).getNumericCellValue()).isEqualTo(1.0);
            assertThat(confirmedRow.getCell(1).getNumericCellValue()).isEqualTo(20.0);
            assertThat(confirmedRow.getCell(4).getNumericCellValue()).isEqualTo(5.0); // 수수료율 0.05 -> 5.0%
            assertThat(confirmedRow.getCell(6).getNumericCellValue()).isEqualTo(123500.0);
            assertThat(confirmedRow.getCell(7).getStringCellValue()).isEqualTo("CONFIRMED");
            assertThat(confirmedRow.getCell(8).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 8, 13, 10, 0));
            assertThat(confirmedRow.getCell(9).getNumericCellValue()).isEqualTo(99.0);

            Row pendingRow = sheet.getRow(2);
            assertThat(pendingRow.getCell(8).getStringCellValue()).isEqualTo("-");
            assertThat(pendingRow.getCell(9).getStringCellValue()).isEqualTo("-");

            assertThat(sheet.getLastRowNum()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("정산이 하나도 없는 행사는 헤더만 있는 빈 엑셀을 반환한다")
    void exportsEmptySheetWhenNoSettlements() throws Exception {
        given(settlementService.getByFair(10L)).willReturn(List.of());

        byte[] bytes = settlementExportService.exportAsExcel(10L);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("정산내역");
            assertThat(sheet.getLastRowNum()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("행사별 매출 요약을 시트 한 장짜리 엑셀로 만든다 - 헤더 + 데이터 행 일치")
    void exportsRevenueSummaryAsExcel() throws Exception {
        given(settlementService.getFairRevenueSummaries()).willReturn(List.of(
                new FairRevenueSummaryResponse(10L, "댕댕펫", 70000L, 30000L, 100000L,
                        new BigDecimal("0.1000"), 10000L, 90000L)
        ));

        byte[] bytes = settlementExportService.exportRevenueSummaryAsExcel();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("행사별 매출 요약");
            assertThat(sheet).isNotNull();

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("행사ID");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("행사명");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("티켓예매 총금액");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("참가비용 총금액");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("전체금액");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("행사업체금액");
            assertThat(header.getCell(7).getStringCellValue()).isEqualTo("플랫폼금액");

            Row row = sheet.getRow(1);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("댕댕펫");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("70000");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("30000");
            assertThat(row.getCell(4).getStringCellValue()).isEqualTo("100000");
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("90000");
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("10000");

            assertThat(sheet.getLastRowNum()).isEqualTo(1);
        }
    }
}
