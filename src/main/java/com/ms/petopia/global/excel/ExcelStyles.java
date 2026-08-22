package com.ms.petopia.global.excel;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;

/**
 * 엑셀 다운로드 전반에서 재사용하는 헤더/데이터 스타일 묶음.
 * 도메인별 ExportService는 시트 구성만 담당하고, 색상·서식은 여기 한 곳에서만 정의해서
 * 엑셀 다운로드마다 디자인이 제각각이 되는 걸 막는다.
 */
public final class ExcelStyles {

    // 프론트 theme.css의 브랜드 톤(--color-leaf, --color-leaf-soft, --color-line)과 맞춘 팔레트.
    public static final Color HEADER_FILL = new Color(0x4F, 0xA8, 0x78);  // --color-leaf보다 한 톤 진하게(대비 확보)
    public static final Color BAND_FILL = new Color(0xED, 0xF8, 0xF1);    // --color-leaf-soft

    private final CellStyle header;
    private final CellStyle labelPlain;
    private final CellStyle labelBand;
    private final CellStyle countPlain;
    private final CellStyle countBand;
    private final CellStyle percentPlain;
    private final CellStyle percentBand;
    private final CellStyle decimalPlain;
    private final CellStyle decimalBand;
    private final CellStyle datePlain;
    private final CellStyle dateBand;

    public ExcelStyles(XSSFWorkbook wb) {
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

        datePlain = baseStyle(wb, false, HorizontalAlignment.RIGHT);
        datePlain.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        dateBand = baseStyle(wb, true, HorizontalAlignment.RIGHT);
        dateBand.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
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

    public CellStyle header() { return header; }
    /** 문자열 라벨/텍스트 셀. */
    public CellStyle label(boolean band) { return band ? labelBand : labelPlain; }
    /** 정수 건수·금액 등 천단위 콤마(#,##0)가 필요한 숫자 셀. */
    public CellStyle count(boolean band) { return band ? countBand : countPlain; }
    public CellStyle percent(boolean band) { return band ? percentBand : percentPlain; }
    public CellStyle decimal(boolean band) { return band ? decimalBand : decimalPlain; }
    public CellStyle date(boolean band) { return band ? dateBand : datePlain; }
}
