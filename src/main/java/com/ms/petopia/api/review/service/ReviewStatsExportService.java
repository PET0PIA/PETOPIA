package com.ms.petopia.api.review.service;

import com.ms.petopia.api.review.dto.CategoryTagRanking;
import com.ms.petopia.api.review.dto.CountItem;
import com.ms.petopia.api.review.dto.FairReviewStatsResponse;
import com.ms.petopia.api.review.dto.TagCountItem;
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
import java.util.Map;

/**
 * 리뷰(태그) 통계 엑셀(.xlsx) export. {@code com.ms.petopia.api.statistics.service.VisitStatsExportService}
 * 와 같은 패턴으로 이미 검증된 {@link FairReviewStatsService#getStats}를 그대로 시트로 옮기고,
 * 스타일은 {@link ExcelStyles}/{@link ExcelSheetHelper}를 공유해서 방문 통계 엑셀과 디자인을 맞춘다.
 */
@Service
@RequiredArgsConstructor
public class ReviewStatsExportService {

    // TagRankingBoard.tsx(프론트)의 fairCategoryLabels와 동일한 매핑을 여기서도 독립적으로 둔다.
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "GUIDE_OPERATION", "안내/운영",
            "SAFETY_HYGIENE", "안전/위생",
            "WAIT_FLOW", "대기/동선",
            "PET_CONVENIENCE", "반려동물 동반 편의성",
            "FACILITY", "시설/편의",
            "PRICE_VALUE", "가격/가치",
            "CONTENT_PROGRAM", "부대행사/콘텐츠"
    );

    // ReviewManagementPage였던 시절 프론트가 쓰던 companionTypeLabels/visitPurposeLabels와 동일.
    private static final Map<String, String> COMPANION_TYPE_LABELS = Map.of(
            "ALONE", "혼자",
            "WITH_PET", "반려동물과 함께",
            "WITH_FAMILY", "가족과 함께",
            "WITH_FRIEND", "친구와 함께"
    );

    private static final Map<String, String> VISIT_PURPOSE_LABELS = Map.of(
            "SHOPPING", "쇼핑",
            "EXPERIENCE", "체험",
            "INFO", "정보 습득",
            "ETC", "기타"
    );

    private final FairReviewStatsService fairReviewStatsService;

    public byte[] exportAsExcel(Long fairId) throws IOException {
        FairReviewStatsResponse stats = fairReviewStatsService.getStats(fairId);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            ExcelStyles styles = new ExcelStyles(wb);

            createSummarySheet(wb, styles, stats);
            createDistributionSheet(wb, styles, "동반유형 분포", "동반유형", stats.companionTypeDistribution(), COMPANION_TYPE_LABELS);
            createDistributionSheet(wb, styles, "방문목적 분포", "방문목적", stats.visitPurposeDistribution(), VISIT_PURPOSE_LABELS);
            createTagRankingSheet(wb, styles, stats.fairTagRankings());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void createSummarySheet(XSSFWorkbook wb, ExcelStyles styles, FairReviewStatsResponse stats) {
        Sheet sheet = wb.createSheet("리뷰 요약");
        ExcelSheetHelper.writeHeader(sheet, styles, "항목", "값");

        Row countRow = sheet.createRow(1);
        ExcelSheetHelper.setCell(countRow, 0, "총 리뷰 수", styles.label(false));
        ExcelSheetHelper.setCell(countRow, 1, stats.reviewCount(), styles.count(false));

        Row revisitRow = sheet.createRow(2);
        ExcelSheetHelper.setCell(revisitRow, 0, "재방문 의향", styles.label(true));
        ExcelSheetHelper.setCell(revisitRow, 1, stats.revisitRate() * 100, styles.percent(true));

        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        ExcelSheetHelper.finalizeSheet(sheet, 1, 2);
    }

    private void createDistributionSheet(XSSFWorkbook wb, ExcelStyles styles, String sheetName,
                                          String labelHeader, List<CountItem> data, Map<String, String> labels) {
        Sheet sheet = wb.createSheet(sheetName);
        ExcelSheetHelper.writeHeader(sheet, styles, labelHeader, "건수", "비율(%)");

        for (int i = 0; i < data.size(); i++) {
            CountItem item = data.get(i);
            Row row = sheet.createRow(i + 1);
            boolean band = i % 2 == 1;
            ExcelSheetHelper.setCell(row, 0, labels.getOrDefault(item.key(), item.key()), styles.label(band));
            ExcelSheetHelper.setCell(row, 1, item.count(), styles.count(band));
            ExcelSheetHelper.setCell(row, 2, item.ratio() * 100, styles.percent(band));
        }

        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 10 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        ExcelSheetHelper.finalizeSheet(sheet, 2, data.size());
    }

    private void createTagRankingSheet(XSSFWorkbook wb, ExcelStyles styles, List<CategoryTagRanking> rankings) {
        Sheet sheet = wb.createSheet("카테고리별 태그 랭킹");
        ExcelSheetHelper.writeHeader(sheet, styles, "카테고리", "평가", "태그", "건수", "비율(%)");

        int rowIdx = 1;
        for (CategoryTagRanking ranking : rankings) {
            String categoryLabel = CATEGORY_LABELS.getOrDefault(ranking.category(), ranking.category());
            rowIdx = writeTagRows(sheet, styles, rowIdx, categoryLabel, "좋았어요", ranking.positiveTop());
            rowIdx = writeTagRows(sheet, styles, rowIdx, categoryLabel, "아쉬웠어요", ranking.negativeTop());
        }

        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 24 * 256);
        sheet.setColumnWidth(3, 10 * 256);
        sheet.setColumnWidth(4, 12 * 256);
        ExcelSheetHelper.finalizeSheet(sheet, 4, rowIdx - 1);
    }

    private int writeTagRows(Sheet sheet, ExcelStyles styles, int startRow,
                              String categoryLabel, String sentimentLabel, List<TagCountItem> items) {
        int rowIdx = startRow;
        for (TagCountItem item : items) {
            Row row = sheet.createRow(rowIdx);
            boolean band = rowIdx % 2 == 0;
            ExcelSheetHelper.setCell(row, 0, categoryLabel, styles.label(band));
            ExcelSheetHelper.setCell(row, 1, sentimentLabel, styles.label(band));
            ExcelSheetHelper.setCell(row, 2, item.label(), styles.label(band));
            ExcelSheetHelper.setCell(row, 3, item.count(), styles.count(band));
            ExcelSheetHelper.setCell(row, 4, item.ratio() * 100, styles.percent(band));
            rowIdx++;
        }
        return rowIdx;
    }
}
