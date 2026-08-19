package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.auth.mapper.FairAdminAssignmentMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FairAdminAccessGuardTest {

    private static final Long FAIR_ID = 10L;
    private static final Long ADMIN_USER_ID = 5L;

    @Mock
    private FairAdminAssignmentMapper fairAdminAssignmentMapper;

    @InjectMocks
    private FairAdminAccessGuard fairAdminAccessGuard;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("SUPER_ADMIN이면 배정 여부를 확인하지 않고 통과한다")
    void checkAssigned_SUPER_ADMIN이면_통과한다() {
        authenticateAs(ADMIN_USER_ID, "SUPER_ADMIN");

        fairAdminAccessGuard.checkAssigned(FAIR_ID);

        verify(fairAdminAssignmentMapper, never()).existsByAdminUserIdAndFairId(any(), any());
    }

    @Test
    @DisplayName("EVENT_ADMIN이 이 행사에 배정돼 있으면 통과한다")
    void checkAssigned_EVENT_ADMIN이_배정돼있으면_통과한다() {
        authenticateAs(ADMIN_USER_ID, "EVENT_ADMIN");
        given(fairAdminAssignmentMapper.existsByAdminUserIdAndFairId(ADMIN_USER_ID, FAIR_ID)).willReturn(true);

        fairAdminAccessGuard.checkAssigned(FAIR_ID);
    }

    @Test
    @DisplayName("EVENT_ADMIN이 이 행사에 배정돼 있지 않으면 ACCESS_DENIED를 던진다")
    void checkAssigned_EVENT_ADMIN이_배정안돼있으면_예외를_던진다() {
        authenticateAs(ADMIN_USER_ID, "EVENT_ADMIN");
        given(fairAdminAssignmentMapper.existsByAdminUserIdAndFairId(ADMIN_USER_ID, FAIR_ID)).willReturn(false);

        assertThatThrownBy(() -> fairAdminAccessGuard.checkAssigned(FAIR_ID))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("requireSuperAdmin: SUPER_ADMIN이면 통과한다")
    void requireSuperAdmin_SUPER_ADMIN이면_통과한다() {
        authenticateAs(ADMIN_USER_ID, "SUPER_ADMIN");

        fairAdminAccessGuard.requireSuperAdmin();

        verify(fairAdminAssignmentMapper, never()).existsByAdminUserIdAndFairId(any(), any());
    }

    @Test
    @DisplayName("requireSuperAdmin: EVENT_ADMIN이면 배정 여부와 무관하게 ACCESS_DENIED를 던진다")
    void requireSuperAdmin_EVENT_ADMIN이면_예외를_던진다() {
        authenticateAs(ADMIN_USER_ID, "EVENT_ADMIN");

        assertThatThrownBy(() -> fairAdminAccessGuard.requireSuperAdmin())
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(fairAdminAssignmentMapper, never()).existsByAdminUserIdAndFairId(any(), any());
    }

    private void authenticateAs(Long userId, String role) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
