package com.ms.petopia.api.statistics.service;

import com.ms.petopia.api.statistics.dto.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VisitStatsExportService {

    // 프론트 theme.css의 브랜드 톤(--color-leaf, --color-leaf-soft, --color-line)과 맞춘 팔레트.
    private static final Color HEADER_FILL = new Color(0x4F, 0xA8, 0x78);   // --color-leaf보다 한 톤 진하게(대비 확보)
    private static final Color BAND_FILL = new Color(0xED, 0xF8, 0xF1);     // --color-leaf-soft

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
        writeHeader(sheet, styles, "항목", "값");

        int r = 1;
        writeCountRow(sheet, styles, r++, "총 방문자 수", stats.getTotalVisitors());
        writeCountRow(sheet, styles, r++, "확정 예약 수", stats.getTotalConfirmedReservations());
        writePercentRow(sheet, styles, r++, "방문율", stats.getVisitRate());
        writeDecimalRow(sheet, styles, r++, "평균 반려동물 나이", stats.getAvgPetAge());
        writeDecimalRow(sheet, styles, r, "방문자당 평균 방문 부스 수", stats.getAvgBoothsPerVisitor());

        sheet.setColumnWidth(0, 26 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        finalizeSheet(sheet, 1, r);
    }

    private void createLabelCountSheet(XSSFWorkbook wb, ExcelStyles styles,
                                        String sheetName, String labelHeader,
                                        List<LabelCountDto> data) {
        Sheet sheet = wb.createSheet(sheetName);
        writeHeader(sheet, styles, labelHeader, "건수");

        for (int i = 0; i < data.size(); i++) {
            int rowIdx = i + 1;
            Row row = sheet.createRow(rowIdx);
            boolean band = i % 2 == 1;
            setCell(row, 0, data.get(i).getLabel(), styles.label(band));
            setCell(row, 1, data.get(i).getCount(), styles.count(band));
        }

        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        finalizeSheet(sheet, 1, data.size());
    }

    private void createPetBreedSheet(XSSFWorkbook wb, ExcelStyles styles, List<PetBreedStatDto> data) {
        Sheet sheet = wb.createSheet("반려동물 품종 분포");
        writeHeader(sheet, styles, "종", "품종", "건수");

        for (int i = 0; i < data.size(); i++) {
            PetBreedStatDto item = data.get(i);
            Row row = sheet.createRow(i + 1);
            boolean band = i % 2 == 1;
            setCell(row, 0, item.getSpecies(), styles.label(band));
            setCell(row, 1, item.getBreed() != null ? item.getBreed() : "미등록", styles.label(band));
            setCell(row, 2, item.getCount(), styles.count(band));
        }

        sheet.setColumnWidth(0, 14 * 256);
        sheet.setColumnWidth(1, 20 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        finalizeSheet(sheet, 2, data.size());
    }

    // ── 행 작성 헬퍼 ──────────────────────────────────────────

    private void writeHeader(Sheet sheet, ExcelStyles styles, String... labels) {
        Row header = sheet.createRow(0);
        header.setHeightInPoints(20f);
        for (int i = 0; i < labels.length; i++) {
            setCell(header, i, labels[i], styles.header());
        }
    }

    private void writeCountRow(Sheet sheet, ExcelStyles styles, int rowIdx, String label, int value) {
        boolean band = rowIdx % 2 == 0;
        Row row = sheet.createRow(rowIdx);
        setCell(row, 0, label, styles.label(band));
        setCell(row, 1, value, styles.count(band));
    }

    private void writePercentRow(Sheet sheet, ExcelStyles styles, int rowIdx, String label, double value) {
        boolean band = rowIdx % 2 == 0;
        Row row = sheet.createRow(rowIdx);
        setCell(row, 0, label, styles.label(band));
        Cell cell = row.createCell(1);
        cell.setCellValue(value);
        cell.setCellStyle(styles.percent(band));
    }

    private void writeDecimalRow(Sheet sheet, ExcelStyles styles, int rowIdx, String label, Double value) {
        boolean band = rowIdx % 2 == 0;
        Row row = sheet.createRow(rowIdx);
        setCell(row, 0, label, styles.label(band));
        if (value != null) {
            Cell cell = row.createCell(1);
            cell.setCellValue(value);
            cell.setCellStyle(styles.decimal(band));
        } else {
            setCell(row, 1, "-", styles.label(band));
        }
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setCell(Row row, int col, int value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /** 열 너비 자동 보정(수동 지정 폭이 내용보다 좁을 때만 넓힘) + 헤더 행 고정 + 자동 필터. */
    private void finalizeSheet(Sheet sheet, int lastCol, int dataRowCount) {
        for (int c = 0; c <= lastCol; c++) {
            int before = sheet.getColumnWidth(c);
            sheet.autoSizeColumn(c);
            int autoWidth = sheet.getColumnWidth(c) + 768; // 여백 padding
            sheet.setColumnWidth(c, Math.max(before, autoWidth));
        }
        sheet.createFreezePane(0, 1);
        if (dataRowCount > 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, dataRowCount, 0, lastCol));
        }
    }

    /** 시트 전반에서 재사용하는 헤더/데이터 스타일 묶음. 짝수·홀수 행 밴딩을 위해 쌍으로 들고 있는다. */
    private static final class ExcelStyles {
        private final CellStyle header;
        private final CellStyle labelPlain;
        private final CellStyle labelBand;
        private final CellStyle countPlain;
        private final CellStyle countBand;
        private final CellStyle percentPlain;
        private final CellStyle percentBand;
        private final CellStyle decimalPlain;
        private final CellStyle decimalBand;

        ExcelStyles(XSSFWorkbook wb) {
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            header = wb.createCellStyle();
            header.setFont(headerFont);
            setFill((XSSFCellStyle) header, HEADER_FILL);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorders(header);

            labelPlain = baseStyle(wb, false, HorizontalAlignment.LEFT);
            labelBand = baseStyle(wb, true, HorizontalAlignment.LEFT);

            countPlain = baseStyle(wb, false, HorizontalAlignment.RIGHT);
            countPlain.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            countBand = baseStyle(wb, true, HorizontalAlignment.RIGHT);
            countBand.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

            percentPlain = baseStyle(wb, false, HorizontalAlignment.RIGHT);
            percentPlain.setDataFormat(wb.createDataFormat().getFormat("0.0\"%\""));
            percentBand = baseStyle(wb, true, HorizontalAlignment.RIGHT);
            percentBand.setDataFormat(wb.createDataFormat().getFormat("0.0\"%\""));

            decimalPlain = baseStyle(wb, false, HorizontalAlignment.RIGHT);
            decimalPlain.setDataFormat(wb.createDataFormat().getFormat("0.0"));
            decimalBand = baseStyle(wb, true, HorizontalAlignment.RIGHT);
            decimalBand.setDataFormat(wb.createDataFormat().getFormat("0.0"));
        }

        private CellStyle baseStyle(XSSFWorkbook wb, boolean band, HorizontalAlignment align) {
            CellStyle style = wb.createCellStyle();
            style.setAlignment(align);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            if (band) {
                setFill((XSSFCellStyle) style, BAND_FILL);
            }
            applyBorders(style);
            return style;
        }

        private void applyBorders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }

        private void setFill(XSSFCellStyle style, Color color) {
            style.setFillForegroundColor(new XSSFColor(color, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        CellStyle header() { return header; }
        CellStyle label(boolean band) { return band ? labelBand : labelPlain; }
        CellStyle count(boolean band) { return band ? countBand : countPlain; }
        CellStyle percent(boolean band) { return band ? percentBand : percentPlain; }
        CellStyle decimal(boolean band) { return band ? decimalBand : decimalPlain; }
    }
}
