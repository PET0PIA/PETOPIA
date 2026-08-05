package com.ms.petopia.api.audit.service;

import com.ms.petopia.api.audit.dto.AuditLogListResponse;
import com.ms.petopia.api.audit.dto.AuditLogRow;
import com.ms.petopia.api.audit.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {
    private final AuditLogMapper auditLogMapper;

    public AuditLogListResponse query(
            String targetType,
            Long targetId,
            Long actorUserId,
            String actionType,
            int page,
            int size
    ){
        long offset = (long) page * size;
        List<AuditLogRow> items;

        if(targetType != null && targetId != null){
            items = auditLogMapper.selectByTarget(targetType, targetId, offset, size);
        }else if(actorUserId != null){
            items = auditLogMapper.selectByActorUserId(actorUserId, offset, size);
        } else if (actionType != null) {
            items = auditLogMapper.selectByActionType(actionType, offset, size);
        } else {
            items = List.of();
        }
        return new AuditLogListResponse(items, page, size);
    }
}
