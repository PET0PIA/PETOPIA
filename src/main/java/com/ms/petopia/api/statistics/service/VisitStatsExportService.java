package com.ms.petopia.api.statistics.service;

import com.ms.petopia.api.statistics.dto.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
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
            CellStyle hs = buildHeaderStyle(wb);

            createSummarySheet(wb, hs, stats);
            createLabelCountSheet(wb, hs, "채널별 분포", "채널", stats.getChannelBreakdown());
            createLabelCountSheet(wb, hs, "성별 분포", "성별", stats.getGenderBreakdown());
            createLabelCountSheet(wb, hs, "연령대 분포", "연령대", stats.getAgeGroupBreakdown());
            createLabelCountSheet(wb, hs, "반려동물 종 분포", "종", stats.getPetSpeciesBreakdown());
            createPetBreedSheet(wb, hs, stats.getPetBreedBreakdown());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void createSummarySheet(XSSFWorkbook wb, CellStyle hs, VisitStatsDto stats) {
        Sheet sheet = wb.createSheet("방문 요약");
        Row header = sheet.createRow(0);
        setCell(header, 0, "항목", hs);
        setCell(header, 1, "값", hs);

        String[][] rows = {
                {"총 방문자 수", String.valueOf(stats.getTotalVisitors())},
                {"확정 예약 수", String.valueOf(stats.getTotalConfirmedReservations())},
                {"방문율 (%)", String.valueOf(stats.getVisitRate())},
                {"평균 반려동물 나이", stats.getAvgPetAge() != null ? stats.getAvgPetAge().toString() : "-"},
                {"방문자당 평균 방문 부스 수", stats.getAvgBoothsPerVisitor() != null ? stats.getAvgBoothsPerVisitor().toString() : "-"},
        };
        for (int i = 0; i < rows.length; i++) {
            Row row = sheet.createRow(i + 1);
            setCell(row, 0, rows[i][0], null);
            setCell(row, 1, rows[i][1], null);
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void createLabelCountSheet(XSSFWorkbook wb, CellStyle hs,
                                       String sheetName, String labelHeader,
                                       List<LabelCountDto> data) {
        Sheet sheet = wb.createSheet(sheetName);
        Row header = sheet.createRow(0);
        setCell(header, 0, labelHeader, hs);
        setCell(header, 1, "건수", hs);

        for (int i = 0; i < data.size(); i++) {
            Row row = sheet.createRow(i + 1);
            setCell(row, 0, data.get(i).getLabel(), null);
            setCell(row, 1, String.valueOf(data.get(i).getCount()), null);
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void createPetBreedSheet(XSSFWorkbook wb, CellStyle hs, List<PetBreedStatDto> data) {
        Sheet sheet = wb.createSheet("반려동물 품종 분포");
        Row header = sheet.createRow(0);
        setCell(header, 0, "종", hs);
        setCell(header, 1, "품종", hs);
        setCell(header, 2, "건수", hs);

        for (int i = 0; i < data.size(); i++) {
            PetBreedStatDto item = data.get(i);
            Row row = sheet.createRow(i + 1);
            setCell(row, 0, item.getSpecies(), null);
            setCell(row, 1, item.getBreed() != null ? item.getBreed() : "미등록", null);
            setCell(row, 2, String.valueOf(item.getCount()), null);
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);
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
