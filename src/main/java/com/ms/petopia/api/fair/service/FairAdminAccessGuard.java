package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.auth.mapper.FairAdminAssignmentMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 홀/부스 슬롯/운영일 관리 API가 공통으로 쓰는 "이 로그인 사용자가 진짜 이 행사 담당자인가"
 * 검증. SecurityConfig가 EVENT_ADMIN/SUPER_ADMIN role인 것까지는 걸러주지만, "이 행사"의
 * 담당자인지는 role만으로 못 가려서(다른 행사 담당 EVENT_ADMIN이 남의 행사를 건드릴 수 있는
 * 문제) 여기서 fair_admin_assignments를 한 번 더 확인한다. SUPER_ADMIN은 배정 여부와 무관하게
 * 항상 통과한다.
 *
 * <p><b>HTTP 요청 전용이다</b> - SecurityContextHolder에 인증 정보가 있어야 동작하므로,
 * 다른 도메인이 빈 주입으로 직접 부르는 내부 호출(예: {@link BoothSlotService#lockBoothSlot})에는
 * 쓰면 안 된다 - 그런 호출엔 HTTP 요청도 SecurityContext도 없어서 여기서 예외가 난다.
 */
@Component
@RequiredArgsConstructor
public class FairAdminAccessGuard {

    private static final String SUPER_ADMIN_AUTHORITY = "ROLE_SUPER_ADMIN";

    private final FairAdminAssignmentMapper fairAdminAssignmentMapper;

    public void checkAssigned(Long fairId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> SUPER_ADMIN_AUTHORITY.equals(authority.getAuthority()));
        if (isSuperAdmin) {
            return;
        }

        Long adminUserId = (Long) authentication.getPrincipal();
        if (!fairAdminAssignmentMapper.existsByAdminUserIdAndFairId(adminUserId, fairId)) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
    }
}
