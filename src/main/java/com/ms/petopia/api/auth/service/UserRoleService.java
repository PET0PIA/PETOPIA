package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/*
    사업자 등록 시 USER -> VENDER update service
    users.role 부여/회수 service
 */
@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final AuthMapper authMapper;
    private final AuditLogService auditLogService;

    //사업자 등록 시 사용
    @Transactional
    public void grantVendorRole(Long userId) {
        int updated = authMapper.updateUserRole(userId, "VENDOR");
        if (updated == 0) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
        auditLogService.record(
                null, ActorType.SYSTEM, null,
                ActionType.ROLE_CHANGE, TargetType.ACCOUNT, userId,
                Map.of("role", "USER"), Map.of("role", "VENDOR")
        );
    }

    //사업자 삭제 시 사용
    @Transactional
    public void revokeVendorRole(Long userId) {
        int updated = authMapper.updateUserRole(userId, "USER");
        if (updated == 0) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
        auditLogService.record(
                null, ActorType.SYSTEM, null,
                ActionType.ROLE_CHANGE, TargetType.ACCOUNT, userId,
                Map.of("role", "VENDOR"), Map.of("role", "USER")
        );
    }
}
