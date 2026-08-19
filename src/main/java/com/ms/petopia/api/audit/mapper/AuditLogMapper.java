package com.ms.petopia.api.audit.mapper;

import com.ms.petopia.api.audit.dto.AuditLogRow;
import com.ms.petopia.api.audit.dto.AuditLogSearchCondition;
import com.ms.petopia.api.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AuditLogMapper {
    void insert(AuditLog auditLog);

    // condition에 채워진 필드만 AND로 조합해 필터링한다. 전부 null이면 조건 없이 전체 조회.
    List<AuditLogRow> search(AuditLogSearchCondition condition);

    long countSearch(AuditLogSearchCondition condition);
}
