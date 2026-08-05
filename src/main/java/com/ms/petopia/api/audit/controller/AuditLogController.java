package com.ms.petopia.api.audit.controller;

import com.ms.petopia.api.audit.dto.AuditLogListResponse;
import com.ms.petopia.api.audit.service.AuditLogQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {
    private final AuditLogQueryService auditLogQueryService;

    @GetMapping
    public AuditLogListResponse getAuditLogs(
        @RequestHeader("X-User-Id") Long requesterId, // 임시 인증
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) Long targetId,
        @RequestParam(required = false) Long actorUserId,
        @RequestParam(required = false) String actionType,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ){
        // TODO 인증 도메인 완성 후 SUPER_ADMIN 권한 검증 추가
        return auditLogQueryService.query(targetType, targetId, actorUserId, actionType, page, size);
    }
}
