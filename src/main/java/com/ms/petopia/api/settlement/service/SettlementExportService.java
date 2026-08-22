package com.ms.petopia.api.settlement.service;

import com.ms.petopia.api.settlement.dto.FairRevenueSummaryResponse;
import com.ms.petopia.api.settlement.dto.SettlementResponse;
import com.ms.petopia.global.excel.ExcelSheetHelper;
import com.ms.petopia.global.excel.ExcelStyles;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 행사 정산내역 엑셀(.xlsx) export. {@code com.ms.petopia.api.statistics.service.VisitStatsExportService}
 * (statistics 도메인)와 같은 패턴 — 별도 조회 로직 없이 이미 검증된
 * {@link SettlementService#getByFair}를 그대로 시트로 옮기기만 한다.
 * 스타일은 두 서비스가 {@link ExcelStyles}/{@link ExcelSheetHelper}를 공유해서 디자인을 맞춘다.
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
            ExcelStyles styles = new ExcelStyles(wb);
            Sheet sheet = wb.createSheet("정산내역");
            ExcelSheetHelper.writeHeader(sheet, styles, HEADERS);

            for (int i = 0; i < settlements.size(); i++) {
                SettlementResponse s = settlements.get(i);
                Row row = sheet.createRow(i + 1);
                boolean band = i % 2 == 1;

                ExcelSheetHelper.setCell(row, 0, s.settlementId(), styles.count(band));
                ExcelSheetHelper.setCell(row, 1, s.businessId(), styles.count(band));
                ExcelSheetHelper.setCell(row, 2, s.grossAmount(), styles.count(band));
                ExcelSheetHelper.setCell(row, 3, s.refundAmount(), styles.count(band));
                if (s.commissionRate() != null) {
                    ExcelSheetHelper.setCell(row, 4, s.commissionRate().doubleValue() * 100, styles.percent(band));
                } else {
                    ExcelSheetHelper.setCell(row, 4, "-", styles.label(band));
                }
                ExcelSheetHelper.setCell(row, 5, s.commissionAmount(), styles.count(band));
                ExcelSheetHelper.setCell(row, 6, s.netAmount(), styles.count(band));
                ExcelSheetHelper.setCell(row, 7, s.status(), styles.label(band));
                if (s.confirmedAt() != null) {
                    ExcelSheetHelper.setCell(row, 8, s.confirmedAt(), styles.date(band));
                } else {
                    ExcelSheetHelper.setCell(row, 8, "-", styles.label(band));
                }
                if (s.confirmedByUserId() != null) {
                    ExcelSheetHelper.setCell(row, 9, s.confirmedByUserId(), styles.count(band));
                } else {
                    ExcelSheetHelper.setCell(row, 9, "-", styles.label(band));
                }
            }

            for (int col = 0; col < HEADERS.length; col++) {
                sheet.setColumnWidth(col, 14 * 256);
            }
            ExcelSheetHelper.finalizeSheet(sheet, HEADERS.length - 1, settlements.size());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static final String[] REVENUE_SUMMARY_HEADERS = {
            "행사ID", "행사명", "티켓예매 총금액", "참가비용 총금액", "전체금액", "수수료율", "행사업체금액", "플랫폼금액"
    };

    /**
     * 행사별 매출 요약(SUPER_ADMIN 정산·수수료 화면) 엑셀 export. 위 정산내역 export와 같은
     * 패턴 - 원래는 자체 String 전용 setCell로 따로 짜여있었는데, 금액·수수료율까지 문자열로
     * 써서 엑셀에서 숫자 정렬·서식이 깨졌다(CodeRabbit 리뷰 지적, PR #222). 공용
     * ExcelSheetHelper/ExcelStyles로 통일해서 위 exportAsExcel과 동일하게 숫자 셀로 쓴다.
     */
    public byte[] exportRevenueSummaryAsExcel() throws IOException {
        List<FairRevenueSummaryResponse> summaries = settlementService.getFairRevenueSummaries();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            ExcelStyles styles = new ExcelStyles(wb);
            Sheet sheet = wb.createSheet("행사별 매출 요약");
            ExcelSheetHelper.writeHeader(sheet, styles, REVENUE_SUMMARY_HEADERS);

            for (int i = 0; i < summaries.size(); i++) {
                FairRevenueSummaryResponse s = summaries.get(i);
                Row row = sheet.createRow(i + 1);
                boolean band = i % 2 == 1;

                ExcelSheetHelper.setCell(row, 0, s.fairId(), styles.count(band));
                ExcelSheetHelper.setCell(row, 1, s.fairName(), styles.label(band));
                ExcelSheetHelper.setCell(row, 2, s.ticketAmount(), styles.count(band));
                ExcelSheetHelper.setCell(row, 3, s.vendorFeeAmount(), styles.count(band));
                ExcelSheetHelper.setCell(row, 4, s.grossAmount(), styles.count(band));
                if (s.commissionRate() != null) {
                    ExcelSheetHelper.setCell(row, 5, s.commissionRate().doubleValue() * 100, styles.percent(band));
                } else {
                    ExcelSheetHelper.setCell(row, 5, "-", styles.label(band));
                }
                ExcelSheetHelper.setCell(row, 6, s.businessAmount(), styles.count(band));
                ExcelSheetHelper.setCell(row, 7, s.platformAmount(), styles.count(band));
            }

            for (int col = 0; col < REVENUE_SUMMARY_HEADERS.length; col++) {
                sheet.setColumnWidth(col, 14 * 256);
            }
            ExcelSheetHelper.finalizeSheet(sheet, REVENUE_SUMMARY_HEADERS.length - 1, summaries.size());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
