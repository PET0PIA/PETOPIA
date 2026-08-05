package com.ms.petopia.api.audit.dto;

import java.util.List;

public record AuditLogListResponse(
        List<AuditLogRow> items, // 실제 로그 목록
        int page, // 현재 페이지 번호
        int size, // 페이지당 개수
        long totalElements, // 전체 건수
        boolean hasNext // 다음 페이지 존재 여부
) {}
