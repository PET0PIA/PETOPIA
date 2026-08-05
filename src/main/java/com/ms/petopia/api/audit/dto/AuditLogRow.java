package com.ms.petopia.api.audit.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AuditLogRow {
    private Long auditId;
    private Long userId;
    private String actorType;
    private String actorRole;
    private String actionType;
    private String targetType;
    private Long targetId;
    private String beforeValue;
    private String afterValue;
    private LocalDateTime occurredAt;
}
