package com.ms.petopia.api.audit.service;

import com.ms.petopia.api.audit.dto.AuditLogListResponse;
import com.ms.petopia.api.audit.dto.AuditLogRow;
import com.ms.petopia.api.audit.dto.AuditLogSearchCondition;
import com.ms.petopia.api.audit.mapper.AuditLogMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ){
        targetType = normalize(targetType);
        actionType = normalize(actionType);

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "시작일이 종료일보다 늦을 수 없습니다.");
        }

        // endDate는 "그 날짜까지 포함"이 직관적인 의미라, DB 비교는 다음날 0시 미만으로 바꿔서 한다.
        LocalDateTime startAt = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endAt = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;

        long offset = (long) page * size;
        AuditLogSearchCondition condition = AuditLogSearchCondition.builder()
                .targetType(targetType)
                .targetId(targetId)
                .actorUserId(actorUserId)
                .actionType(actionType)
                .startAt(startAt)
                .endAt(endAt)
                .offset(offset)
                .limit(size)
                .build();

        List<AuditLogRow> items = auditLogMapper.search(condition);
        long total = auditLogMapper.countSearch(condition);
        boolean hasNext = offset + items.size() < total;
        return new AuditLogListResponse(items, page, size, total, hasNext);
    }

    private String normalize(String value) {
        return (value != null && !value.isBlank()) ? value.strip() : null;
    }
}
