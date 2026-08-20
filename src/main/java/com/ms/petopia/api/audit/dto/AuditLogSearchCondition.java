package com.ms.petopia.api.audit.dto;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 감사 로그 검색 조건. targetType/targetId는 항상 짝으로만 필터에 반영된다(대상종류 없이
 * ID만으로는 서로 다른 target_type에 걸친 같은 번호를 가리킬 수 있어 모호하다) - 나머지
 * 필드는 서로 독립적으로 조합 가능하다. AuditLogMapper#search/countSearch에 그대로 넘어가는
 * 읽기 전용 값이라 record로 둔다.
 */
@Builder
public record AuditLogSearchCondition(
        String targetType,
        Long targetId,
        Long actorUserId,
        String actionType,
        LocalDateTime startAt,
        LocalDateTime endAt,
        long offset,
        int limit
) {}
