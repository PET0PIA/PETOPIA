package com.ms.petopia.api.settlement.service;

import com.ms.petopia.api.settlement.dto.FairRevenueSummaryResponse;
import com.ms.petopia.api.settlement.dto.SettlementResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 행사 정산내역 엑셀(.xlsx) export. {@code com.ms.petopia.api.statistics.service.VisitStatsExportService}
 * (statistics 도메인)와 같은 패턴 — 별도 조회 로직 없이 이미 검증된
 * {@link SettlementService#getByFair}를 그대로 시트로 옮기기만 한다.
 */
@Service
@RequiredArgsConstructor
public class SettlementExportService {

    private static final String[] HEADERS = {
            "정산ID", "업체ID", "총참가비", "환불액", "수수료율", "수수료액", "정산액", "상태", "확정일시", "확정자ID"
    };

    private final SettlementService settlementService;

    public byte[] exportAsExcel(Long fairId) throws IOException {
        List<SettlementResponse> settlements = settlementService.getByFair(fairId);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("정산내역");
            CellStyle headerStyle = buildHeaderStyle(wb);

            Row header = sheet.createRow(0);
            for (int col = 0; col < HEADERS.length; col++) {
                setCell(header, col, HEADERS[col], headerStyle);
            }

            for (int i = 0; i < settlements.size(); i++) {
                SettlementResponse s = settlements.get(i);
                Row row = sheet.createRow(i + 1);
                setCell(row, 0, String.valueOf(s.settlementId()), null);
                setCell(row, 1, String.valueOf(s.businessId()), null);
                setCell(row, 2, String.valueOf(s.grossAmount()), null);
                setCell(row, 3, String.valueOf(s.refundAmount()), null);
                setCell(row, 4, s.commissionRate() != null ? s.commissionRate().toString() : "-", null);
                setCell(row, 5, String.valueOf(s.commissionAmount()), null);
                setCell(row, 6, String.valueOf(s.netAmount()), null);
                setCell(row, 7, s.status(), null);
                setCell(row, 8, s.confirmedAt() != null ? s.confirmedAt().toString() : "-", null);
                setCell(row, 9, s.confirmedByUserId() != null ? String.valueOf(s.confirmedByUserId()) : "-", null);
            }

            for (int col = 0; col < HEADERS.length; col++) {
                sheet.autoSizeColumn(col);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static final String[] REVENUE_SUMMARY_HEADERS = {
            "행사ID", "행사명", "티켓예매 총금액", "참가비용 총금액", "전체금액", "수수료율", "행사업체금액", "플랫폼금액"
    };

    /** 행사별 매출 요약(SUPER_ADMIN 정산·수수료 화면) 엑셀 export. 위 정산내역 export와 같은 패턴. */
    public byte[] exportRevenueSummaryAsExcel() throws IOException {
        List<FairRevenueSummaryResponse> summaries = settlementService.getFairRevenueSummaries();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("행사별 매출 요약");
            CellStyle headerStyle = buildHeaderStyle(wb);

            Row header = sheet.createRow(0);
            for (int col = 0; col < REVENUE_SUMMARY_HEADERS.length; col++) {
                setCell(header, col, REVENUE_SUMMARY_HEADERS[col], headerStyle);
            }

            for (int i = 0; i < summaries.size(); i++) {
                FairRevenueSummaryResponse s = summaries.get(i);
                Row row = sheet.createRow(i + 1);
                setCell(row, 0, String.valueOf(s.fairId()), null);
                setCell(row, 1, s.fairName(), null);
                setCell(row, 2, String.valueOf(s.ticketAmount()), null);
                setCell(row, 3, String.valueOf(s.vendorFeeAmount()), null);
                setCell(row, 4, String.valueOf(s.grossAmount()), null);
                setCell(row, 5, s.commissionRate() != null ? s.commissionRate().toString() : "-", null);
                setCell(row, 6, String.valueOf(s.businessAmount()), null);
                setCell(row, 7, String.valueOf(s.platformAmount()), null);
            }

            for (int col = 0; col < REVENUE_SUMMARY_HEADERS.length; col++) {
                sheet.autoSizeColumn(col);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }
}
