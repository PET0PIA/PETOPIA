package com.ms.petopia.api.audit.mapper;

import com.ms.petopia.api.audit.dto.AuditLogRow;
import com.ms.petopia.api.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuditLogMapper {
    void insert(AuditLog auditLog);

    // 특정 엔티티에 무슨 일이 있었는지
    List<AuditLogRow> selectByTarget(
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    // 특정 관리자가 뭘 했는지
    List<AuditLogRow> selectByActorUserId(
            @Param("userId") Long userId,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    // 특정 액션 타입만 필터링
    List<AuditLogRow> selectByActionType(
            @Param("actionType") String actionType,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    Long countByTarget(
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId
    );

    Long countByActorUserId(@Param("userId") Long userId);
}
