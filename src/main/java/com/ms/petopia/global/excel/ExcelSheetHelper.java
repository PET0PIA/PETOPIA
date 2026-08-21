package com.ms.petopia.global.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

import java.time.LocalDateTime;

/**
 * ExcelStyles와 짝을 이루는 시트 작성 헬퍼. 헤더 행 작성, 셀 값 세팅,
 * 열 너비/헤더고정/자동필터 마무리처럼 어느 ExportService에서든 똑같이 반복되는 부분을 모아둔다.
 */
public final class ExcelSheetHelper {

    private ExcelSheetHelper() {
    }

    public static void writeHeader(Sheet sheet, ExcelStyles styles, String... labels) {
        Row header = sheet.createRow(0);
        header.setHeightInPoints(20f);
        for (int i = 0; i < labels.length; i++) {
            setCell(header, i, labels[i], styles.header());
        }
    }

    public static void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    public static void setCell(Row row, int col, long value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    public static void setCell(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    public static void setCell(Row row, int col, LocalDateTime value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /** 열 너비 자동 보정(수동 지정 폭이 내용보다 좁을 때만 넓힘) + 헤더 행 고정 + 자동 필터. */
    public static void finalizeSheet(Sheet sheet, int lastCol, int dataRowCount) {
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
}
