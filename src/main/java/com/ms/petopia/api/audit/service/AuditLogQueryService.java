package com.ms.petopia.api.audit.service;

import com.ms.petopia.api.audit.dto.AuditLogListResponse;
import com.ms.petopia.api.audit.dto.AuditLogRow;
import com.ms.petopia.api.audit.mapper.AuditLogMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {
    private final AuditLogMapper auditLogMapper;

    @Transactional(readOnly = true)
    public AuditLogListResponse query(
            String targetType,
            Long targetId,
            Long actorUserId,
            String actionType,
            int page,
            int size
    ){
        targetType = (targetType != null && !targetType.isBlank()) ? targetType.strip() : null;
        actionType = (actionType != null && !actionType.isBlank()) ? actionType.strip() : null;

        long offset = (long) page * size;
        List<AuditLogRow> items;
        long total;

        if(targetType != null && targetId != null){
            items = auditLogMapper.selectByTarget(targetType, targetId, offset, size);
            total = auditLogMapper.countByTarget(targetType, targetId);
        }else if(actorUserId != null){
            items = auditLogMapper.selectByActorUserId(actorUserId, offset, size);
            total = auditLogMapper.countByActorUserId(actorUserId);
        } else if (actionType != null) {
            items = auditLogMapper.selectByActionType(actionType, offset, size);
            total = auditLogMapper.countByActionType(actionType);
        } else {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "targetType+targetId, actorUserId, actionType 중 하나는 필수입니다.");
        }
        boolean hasNext = offset + items.size() < total;
        return new AuditLogListResponse(items, page, size, total, hasNext);
    }
}
