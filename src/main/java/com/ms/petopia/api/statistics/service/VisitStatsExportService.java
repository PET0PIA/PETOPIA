package com.ms.petopia.api.statistics.service;

import com.ms.petopia.api.statistics.dto.*;
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

@Service
@RequiredArgsConstructor
public class VisitStatsExportService {

    private final ReservationDashboardService dashboardService;

    public byte[] exportAsExcel(Long fairId) throws IOException {
        VisitStatsDto stats = dashboardService.getVisitStats(fairId);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            ExcelStyles styles = new ExcelStyles(wb);

            createSummarySheet(wb, styles, stats);
            createLabelCountSheet(wb, styles, "채널별 분포", "채널", stats.getChannelBreakdown());
            createLabelCountSheet(wb, styles, "성별 분포", "성별", stats.getGenderBreakdown());
            createLabelCountSheet(wb, styles, "연령대 분포", "연령대", stats.getAgeGroupBreakdown());
            createLabelCountSheet(wb, styles, "반려동물 종 분포", "종", stats.getPetSpeciesBreakdown());
            createPetBreedSheet(wb, styles, stats.getPetBreedBreakdown());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void createSummarySheet(XSSFWorkbook wb, ExcelStyles styles, VisitStatsDto stats) {
        Sheet sheet = wb.createSheet("방문 요약");
        ExcelSheetHelper.writeHeader(sheet, styles, "항목", "값");

        int r = 1;
        writeCountRow(sheet, styles, r++, "총 방문자 수", stats.getTotalVisitors());
        writeCountRow(sheet, styles, r++, "확정 예약 수", stats.getTotalConfirmedReservations());
        writePercentRow(sheet, styles, r++, "방문율", stats.getVisitRate());
        writeDecimalRow(sheet, styles, r++, "평균 반려동물 나이", stats.getAvgPetAge());
        writeDecimalRow(sheet, styles, r, "방문자당 평균 방문 부스 수", stats.getAvgBoothsPerVisitor());

        sheet.setColumnWidth(0, 26 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        ExcelSheetHelper.finalizeSheet(sheet, 1, r);
    }

    private void createLabelCountSheet(XSSFWorkbook wb, ExcelStyles styles,
                                        String sheetName, String labelHeader,
                                        List<LabelCountDto> data) {
        Sheet sheet = wb.createSheet(sheetName);
        ExcelSheetHelper.writeHeader(sheet, styles, labelHeader, "건수");

        for (int i = 0; i < data.size(); i++) {
            int rowIdx = i + 1;
            Row row = sheet.createRow(rowIdx);
            boolean band = i % 2 == 1;
            ExcelSheetHelper.setCell(row, 0, data.get(i).getLabel(), styles.label(band));
            ExcelSheetHelper.setCell(row, 1, data.get(i).getCount(), styles.count(band));
        }

        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        ExcelSheetHelper.finalizeSheet(sheet, 1, data.size());
    }

    private void createPetBreedSheet(XSSFWorkbook wb, ExcelStyles styles, List<PetBreedStatDto> data) {
        Sheet sheet = wb.createSheet("반려동물 품종 분포");
        ExcelSheetHelper.writeHeader(sheet, styles, "종", "품종", "건수");

        for (int i = 0; i < data.size(); i++) {
            PetBreedStatDto item = data.get(i);
            Row row = sheet.createRow(i + 1);
            boolean band = i % 2 == 1;
            ExcelSheetHelper.setCell(row, 0, item.getSpecies(), styles.label(band));
            ExcelSheetHelper.setCell(row, 1, item.getBreed() != null ? item.getBreed() : "미등록", styles.label(band));
            ExcelSheetHelper.setCell(row, 2, item.getCount(), styles.count(band));
        }

        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 20 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        ExcelSheetHelper.finalizeSheet(sheet, 2, data.size());
    }

    // ── 행 작성 헬퍼 ──────────────────────────────────────────

    private void writeCountRow(Sheet sheet, ExcelStyles styles, int rowIdx, String label, int value) {
        boolean band = rowIdx % 2 == 0;
        Row row = sheet.createRow(rowIdx);
        ExcelSheetHelper.setCell(row, 0, label, styles.label(band));
        ExcelSheetHelper.setCell(row, 1, value, styles.count(band));
    }

    private void writePercentRow(Sheet sheet, ExcelStyles styles, int rowIdx, String label, double value) {
        boolean band = rowIdx % 2 == 0;
        Row row = sheet.createRow(rowIdx);
        ExcelSheetHelper.setCell(row, 0, label, styles.label(band));
        ExcelSheetHelper.setCell(row, 1, value, styles.percent(band));
    }

    private void writeDecimalRow(Sheet sheet, ExcelStyles styles, int rowIdx, String label, Double value) {
        boolean band = rowIdx % 2 == 0;
        Row row = sheet.createRow(rowIdx);
        ExcelSheetHelper.setCell(row, 0, label, styles.label(band));
        if (value != null) {
            ExcelSheetHelper.setCell(row, 1, (double) value, styles.decimal(band));
        } else {
            ExcelSheetHelper.setCell(row, 1, "-", styles.label(band));
        }
    }
}
