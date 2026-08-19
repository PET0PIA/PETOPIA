package com.ms.petopia.api.user.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.service.AccountSuspensionStore;
import com.ms.petopia.api.user.dto.UserMeResponse;
import com.ms.petopia.api.user.dto.UserUpdateRequest;
import com.ms.petopia.api.user.mapper.UserMapper;
import com.ms.petopia.api.user.mapper.UserWithdrawalEligibilityMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserWithdrawalEligibilityMapper userWithdrawalEligibilityMapper;
    @Mock
    private AccountSuspensionStore accountSuspensionStore;
    @InjectMocks
    private UserService userService;

    @Test
    void getMe_존재하는유저면_응답으로변환한다() {
        given(userMapper.selectUserById(USER_ID)).willReturn(user());

        UserMeResponse result = userService.getMe(USER_ID);

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.email()).isEqualTo("test@petopia.com");
        assertThat(result.nickname()).isEqualTo("나경");
        assertThat(result.role()).isEqualTo("USER");
        assertThat(result.emailVerified()).isTrue();
    }

    @Test
    void getMe_유저가없으면_USER_NOT_FOUND를던진다() {
        given(userMapper.selectUserById(USER_ID)).willReturn(null);

        assertThatThrownBy(() -> userService.getMe(USER_ID))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updateMe_일부필드만보내면_그필드만매퍼에넘긴다() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setNickname("새닉네임");
        given(userMapper.updateUserProfile(eq(USER_ID), eq("새닉네임"), isNull(), isNull(), isNull(), isNull())).willReturn(1);
        given(userMapper.selectUserById(USER_ID)).willReturn(user());

        userService.updateMe(USER_ID, request);

        verify(userMapper).updateUserProfile(USER_ID, "새닉네임", null, null, null, null);
    }

    @Test
    void updateMe_아무필드도안보내면_매퍼를호출하지않고_현재값을그대로반환한다() {
        UserUpdateRequest request = new UserUpdateRequest();
        given(userMapper.selectUserById(USER_ID)).willReturn(user());

        UserMeResponse result = userService.updateMe(USER_ID, request);

        verify(userMapper, never()).updateUserProfile(any(), any(), any(), any(), any(), any());
        assertThat(result.nickname()).isEqualTo("나경");
    }

    @Test
    void updateMe_생일만보내면_그필드만매퍼에넘긴다() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setBirthDate(LocalDate.of(1998, 3, 15));
        given(userMapper.updateUserProfile(eq(USER_ID), isNull(), eq(LocalDate.of(1998, 3, 15)), isNull(), isNull(), isNull())).willReturn(1);
        given(userMapper.selectUserById(USER_ID)).willReturn(user());

        userService.updateMe(USER_ID, request);

        verify(userMapper).updateUserProfile(USER_ID, null, LocalDate.of(1998, 3, 15), null, null, null);
    }

    @Test
    void updateMe_대상유저가없으면_USER_NOT_FOUND를던진다() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setAddress("서울시 노원구");
        given(userMapper.updateUserProfile(eq(USER_ID), isNull(), isNull(), isNull(), isNull(), eq("서울시 노원구"))).willReturn(0);

        assertThatThrownBy(() -> userService.updateMe(USER_ID, request))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void withdraw_존재하지_않는_유저면_USER_NOT_FOUND를_던진다() {
        given(userMapper.selectUserById(USER_ID)).willReturn(null);

        assertThatThrownBy(() -> userService.withdraw(USER_ID))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userMapper, never()).withdrawUser(anyLong(), any(), any());
    }

    @Test
    void withdraw_진행중인_예약이_있으면_WITHDRAWAL_BLOCKED를_던진다() {
        given(userMapper.selectUserById(USER_ID)).willReturn(user());
        given(userWithdrawalEligibilityMapper.existsActiveReservation(USER_ID)).willReturn(true);

        assertThatThrownBy(() -> userService.withdraw(USER_ID))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWAL_BLOCKED);

        verify(userMapper, never()).withdrawUser(anyLong(), any(), any());
    }

    @Test
    void withdraw_대기중인_결제가_있으면_WITHDRAWAL_BLOCKED를_던진다() {
        given(userMapper.selectUserById(USER_ID)).willReturn(user());
        given(userWithdrawalEligibilityMapper.existsActiveReservation(USER_ID)).willReturn(false);
        given(userWithdrawalEligibilityMapper.existsPendingPayment(USER_ID)).willReturn(true);

        assertThatThrownBy(() -> userService.withdraw(USER_ID))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWAL_BLOCKED);

        verify(userMapper, never()).withdrawUser(anyLong(), any(), any());
    }

    @Test
    void withdraw_막을_사유가_없으면_이메일을_치환하고_계정을_정지시킨다() {
        given(userMapper.selectUserById(USER_ID)).willReturn(user());
        given(userWithdrawalEligibilityMapper.existsActiveReservation(USER_ID)).willReturn(false);
        given(userWithdrawalEligibilityMapper.existsPendingPayment(USER_ID)).willReturn(false);
        given(userMapper.withdrawUser(eq(USER_ID), contains("withdrawn_" + USER_ID), eq("test@petopia.com")))
                .willReturn(1);

        userService.withdraw(USER_ID);

        verify(userMapper).withdrawUser(eq(USER_ID), contains("@deleted.petopia.local"), eq("test@petopia.com"));
        verify(accountSuspensionStore).suspend(USER_ID);
    }

    @Test
    void withdraw_동시요청으로_이미_처리됐으면_USER_NOT_FOUND를_던진다() {
        given(userMapper.selectUserById(USER_ID)).willReturn(user());
        given(userWithdrawalEligibilityMapper.existsActiveReservation(USER_ID)).willReturn(false);
        given(userWithdrawalEligibilityMapper.existsPendingPayment(USER_ID)).willReturn(false);
        given(userMapper.withdrawUser(anyLong(), any(), any())).willReturn(0);

        assertThatThrownBy(() -> userService.withdraw(USER_ID))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(accountSuspensionStore, never()).suspend(any());
    }

    private User user() {
        return User.builder()
                .userId(USER_ID)
                .email("test@petopia.com")
                .nickname("나경")
                .birthDate(LocalDate.of(1998, 3, 15))
                .phone("01011112222")
                .gender("여성")
                .address("서울시 노원구")
                .role("USER")
                .status("ACTIVE")
                .emailVerified(true)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }
}
