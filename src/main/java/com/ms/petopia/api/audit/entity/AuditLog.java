package com.ms.petopia.api.audit.entity;

import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    private Long auditId;
    private Long userId; // SYSTEM 행위이면 null
    private ActorType actorType;
    private ActionType actionType;
    private TargetType targetType;
    private Long targetId;
    private String beforeValue; // JSON 직렬화 문자열, 신규 액션 null
    private String afterValue; // JSON, 삭제 액션은 null
    private LocalDateTime occurredAt;
}
