package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.auth.domain.FairAdminAssignment;
import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.dto.AdminAccountListItemResponse;
import com.ms.petopia.api.auth.dto.EmailLoginRequest;
import com.ms.petopia.api.auth.dto.TokenPair;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.auth.mapper.FairAdminAssignmentMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.security.TokenHashUtil;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AuthMapper authMapper;
    private final FairAdminAssignmentMapper fairAdminAssignmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final AccountSuspensionStore accountSuspensionStore;
    private final AuditLogService auditLogService;

    /**
     * 행사 승인 시 별도의 관리자 계정을 만들지 않고 신청자의 기존 계정을 그대로 사용한다.
     * USER는 EVENT_ADMIN으로 승격하고, 이미 EVENT_ADMIN이면 role을 유지한 채 담당 행사만
     * 추가한다. 신청서의 managerEmail은 연락처 입력값일 수 있으므로 권한 부여나 메일 수신자
     * 판단에 사용하지 않고, applicantUserId로 조회한 회원의 이메일만 신뢰한다.
     */
    @Transactional
    public Long assignApplicantAsEventAdmin(Long fairId, Long applicantUserId) {
        // 사업자 저장·승인과 같은 users 행 잠금을 사용해 EVENT_ADMIN/VENDOR 역할 변경을 직렬화한다.
        User applicant = authMapper.selectUserByIdForUpdate(applicantUserId);
        if (applicant == null) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
        if ("USER".equals(applicant.getRole())) {
            // 기존 개인 데이터는 users 행에 그대로 남고 role만 변경된다.
            authMapper.updateUserRole(applicantUserId, "EVENT_ADMIN");
        } else if (!"EVENT_ADMIN".equals(applicant.getRole())) {
            // VENDOR/SUPER_ADMIN 등 정책상 행사 신청 대상이 아닌 역할은 승인 단계에서도 방어한다.
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
        // 한 EVENT_ADMIN이 여러 행사를 맡을 수 있으므로 계정을 추가 생성하지 않고 배정 행만 늘린다.
        insertAssignment(fairId, applicantUserId, applicantUserId);
        return applicantUserId;
    }

    /** 역할은 계정 단위, 실제 관리 범위는 fair_admin_assignments의 행사 단위로 제한한다. */
    private void insertAssignment(Long fairId, Long adminUserId, Long requesterUserId) {
        FairAdminAssignment fairAdminAssignment = FairAdminAssignment.builder()
                .adminUserId(adminUserId)
                .requesterUserId(requesterUserId)
                .fairId(fairId)
                .build();
        try {
            fairAdminAssignmentMapper.insertFairAdminAssignment(fairAdminAssignment);
        } catch (DuplicateKeyException e) {
            //동시 승인 요청이 겹쳐 fair_id UNIQUE 제약에 걸린 경우 500 대신 409로 응답
            throw new CommonException(ErrorCode.FAIR_ADMIN_ALREADY_ASSIGNED, e);
        }
    }

    //SUPER_ADMIN 전용 로그인
    public TokenPair adminLogin(EmailLoginRequest request) {
        User user = authMapper.selectUserByEmail(request.getEmail());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CommonException(ErrorCode.INVALID_LOGIN);
        }

        if (!user.getRole().equals("SUPER_ADMIN")) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }

        if (user.getStatus().equals("INACTIVE")) {
            throw new CommonException(ErrorCode.ACCOUNT_INACTIVE);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());
        String refreshTokenHash = TokenHashUtil.sha256(refreshToken);
        refreshTokenStore.save(refreshTokenHash, user.getUserId(), Duration.ofDays(14));

        return new TokenPair(accessToken, refreshToken);
    }


    //관리자 계정 목록 조회
    public List<AdminAccountListItemResponse> getAdminAccounts() {
        return authMapper.selectAdminAccounts().stream()
                .map(row -> new AdminAccountListItemResponse(
                        row.getUserId(),
                        row.getEmail(),
                        row.getNickname(),
                        row.getStatus(),
                        row.getFairId(),
                        row.getFairName(),
                        row.getOperationStartDate(),
                        row.getOperationEndDate()
                ))
                .toList();
    }

    //관리자 계정 정지/정지 해제
    @Transactional
    public void updateAccountStatus(Long userId, String status) {
        int updated = authMapper.updateUserStatus(userId, status);
        if (updated == 0) {
            //대상 userId의 계정 자체가 없는 경우
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }

        if ("INACTIVE".equals(status)) {
            auditLogService.record(
                    resolveCurrentUserId(),
                    ActorType.ADMIN,
                    "SUPER_ADMIN",
                    ActionType.ACCOUNT_DEACTIVATE,
                    TargetType.ACCOUNT,
                    userId,
                    Map.of("status", "ACTIVE"),
                    Map.of("status", "INACTIVE")
            );
        }

        //DB 커밋이 성공한 뒤에만 Redis denylist에 반영한다
        deferOrRunNow(() -> {
            if (status.equals("INACTIVE")) {
                accountSuspensionStore.suspend(userId);
            } else {
                accountSuspensionStore.reactivate(userId);
            }
        });
    }

    private Long resolveCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Long id) {
                return id;
            }
        } catch (Exception ignored) {}
        return null;
    }

    //현재 진행 중인 @Transactional이 있으면 그 커밋 성공 후로 실행을 미루고 없으면 즉시 실행한다
    private void deferOrRunNow(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

}
