package com.ms.petopia.api.user.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.service.AccountSuspensionStore;
import com.ms.petopia.api.user.dto.UserMeResponse;
import com.ms.petopia.api.user.dto.UserUpdateRequest;
import com.ms.petopia.api.user.mapper.UserMapper;
import com.ms.petopia.api.user.mapper.UserWithdrawalEligibilityMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class UserService {

    //탈퇴 시 원래 이메일을 대체할 합성 이메일의 도메인 부분. userId만 붙이면 유일성이 보장된다
    private static final String WITHDRAWN_EMAIL_DOMAIN = "@deleted.petopia.local";

    private final UserMapper userMapper;
    private final UserWithdrawalEligibilityMapper userWithdrawalEligibilityMapper;
    private final AccountSuspensionStore accountSuspensionStore;

    public UserMeResponse getMe(Long userId) {
        User user = userMapper.selectUserById(userId);
        if (user == null) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
        return toResponse(user);
    }

    //부분 수정. null이 아닌 것만 반영
    @Transactional
    public UserMeResponse updateMe(Long userId, UserUpdateRequest request) {
        //전부 null이면 SET절이 비는 것도 방지
        boolean hasAnyField = request.getNickname() != null
                || request.getBirthDate() != null
                || request.getPhone() != null
                || request.getGender() != null
                || request.getAddress() != null;

        if (hasAnyField) {
            int updated = userMapper.updateUserProfile(
                    userId,
                    request.getNickname(),
                    request.getBirthDate(),
                    request.getPhone(),
                    request.getGender(),
                    request.getAddress()
            );
            if (updated == 0) {
                throw new CommonException(ErrorCode.USER_NOT_FOUND);
            }
        }

        return getMe(userId);
    }

    //회원 탈퇴. 진행 중인 예약/결제가 있으면 막고, 없으면 소프트 삭제 + 이메일 치환 처리한다
    @Transactional
    public void withdraw(Long userId) {
        User user = userMapper.selectUserById(userId);
        if (user == null) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }

        if (userWithdrawalEligibilityMapper.existsActiveReservation(userId)
                || userWithdrawalEligibilityMapper.existsPendingPayment(userId)) {
            throw new CommonException(ErrorCode.WITHDRAWAL_BLOCKED);
        }

        String syntheticEmail = "withdrawn_" + userId + WITHDRAWN_EMAIL_DOMAIN;
        int updated = userMapper.withdrawUser(userId, syntheticEmail, user.getEmail());
        if (updated == 0) {
            //조회 이후 동시 요청으로 이미 탈퇴 처리된 경우
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }

        //DB 커밋이 성공한 뒤에만 Redis에 반영한다
        //이미 발급된 access token을 즉시 무효화한다
        deferOrRunNow(() -> accountSuspensionStore.suspend(userId));
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

    private UserMeResponse toResponse(User user) {
        return new UserMeResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getBirthDate(),
                user.getPhone(),
                user.getGender(),
                user.getAddress(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}
