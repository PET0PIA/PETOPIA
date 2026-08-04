package com.ms.petopia.api.audit.service;

import com.ms.petopia.api.audit.entity.AuditLog;
import com.ms.petopia.api.audit.mapper.AuditLogMapper;
import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper; // JSON 변환용

    public void record(
        Long userId,
        ActorType actorType,
        String actorRole,
        ActionType actionType,
        TargetType targetType,
        Long targetId,
        Object before,
        Object after
    ){
        AuditLog log = AuditLog.builder()
                .userId(userId)
                .actorType(actorType)
                .actorRole(actorRole)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .beforeValue(toJson(before))
                .afterValue(toJson(after))
                .occurredAt(LocalDateTime.now())
                .build();
        auditLogMapper.insert(log);
    }

    // Object -> JSON
    private String toJson(Object obj){
        if (obj == null) return null;
        try{
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e){
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}
