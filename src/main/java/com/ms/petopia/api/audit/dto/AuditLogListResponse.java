package com.ms.petopia.api.audit.dto;

import java.util.List;

public record AuditLogListResponse(
        List<AuditLogRow> items, // 실제 로그 목록
        int page, // 현재 페이지 번호
        int size // 페이지당 개수
) {}
