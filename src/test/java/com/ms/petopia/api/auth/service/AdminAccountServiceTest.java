package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.FairAdminAssignment;
import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.auth.mapper.FairAdminAssignmentMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long APPLICANT_USER_ID = 5L;
    private static final Long NEW_ADMIN_ID = 100L;
    private static final String MANAGER_NAME = "김담당";
    private static final String MANAGER_EMAIL = "manager@fair.com";
    private static final String MANAGER_PHONE = "010-9999-8888";

    @Mock
    private AuthMapper authMapper;
    @Mock
    private FairAdminAssignmentMapper fairAdminAssignmentMapper;
    @Mock
    private MailService mailService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private AdminAccountService adminAccountService;

    @Test
    void issueEventAdminAccount_managerEmail이_이미존재하면_DUPLICATED_EMAIL을_던지고_아무것도_하지않는다() {
        given(authMapper.selectUserByEmail(MANAGER_EMAIL)).willReturn(applicant());

        assertThatThrownBy(() -> adminAccountService.issueEventAdminAccount(
                FAIR_ID, APPLICANT_USER_ID, MANAGER_NAME, MANAGER_EMAIL, MANAGER_PHONE))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATED_EMAIL);

        verify(authMapper, never()).insertUser(any());
        verify(fairAdminAssignmentMapper, never()).insertFairAdminAssignment(any());
        verify(mailService, never()).sendAdminAccountIssueEmail(any(), any());
    }

    @Test
    void issueEventAdminAccount_managerPhone이있으면_그값을_그대로_사용한다() {
        givenHappyPath();

        adminAccountService.issueEventAdminAccount(
                FAIR_ID, APPLICANT_USER_ID, MANAGER_NAME, MANAGER_EMAIL, MANAGER_PHONE);

        User saved = capturedUser();
        assertThat(saved.getPhone()).isEqualTo(MANAGER_PHONE);
    }

    @Test
    void issueEventAdminAccount_managerPhone이없으면_신청자_전화번호로_대체한다() {
        givenHappyPath();

        adminAccountService.issueEventAdminAccount(
                FAIR_ID, APPLICANT_USER_ID, MANAGER_NAME, MANAGER_EMAIL, null);

        User saved = capturedUser();
        assertThat(saved.getPhone()).isEqualTo(applicant().getPhone());
    }

    @Test
    void issueEventAdminAccount_정상발급시_EVENT_ADMIN_role과_신청자_프로필로_계정을_만든다() {
        givenHappyPath();

        adminAccountService.issueEventAdminAccount(
                FAIR_ID, APPLICANT_USER_ID, MANAGER_NAME, MANAGER_EMAIL, MANAGER_PHONE);

        User saved = capturedUser();
        assertThat(saved.getEmail()).isEqualTo(MANAGER_EMAIL);
        assertThat(saved.getNickname()).isEqualTo(MANAGER_NAME);
        assertThat(saved.getRole()).isEqualTo("EVENT_ADMIN");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.isEmailVerified()).isTrue();
        assertThat(saved.getBirthDate()).isEqualTo(applicant().getBirthDate());
        assertThat(saved.getGender()).isEqualTo(applicant().getGender());
        assertThat(saved.getAddress()).isEqualTo(applicant().getAddress());
        assertThat(saved.isAgreedTerms()).isTrue();
        assertThat(saved.isAgreedPrivacy()).isTrue();
    }

    @Test
    void issueEventAdminAccount_정상발급시_fair_admin_assignments를_새계정id로_기록한다() {
        givenHappyPath();

        adminAccountService.issueEventAdminAccount(
                FAIR_ID, APPLICANT_USER_ID, MANAGER_NAME, MANAGER_EMAIL, MANAGER_PHONE);

        ArgumentCaptor<FairAdminAssignment> captor = ArgumentCaptor.forClass(FairAdminAssignment.class);
        verify(fairAdminAssignmentMapper).insertFairAdminAssignment(captor.capture());
        FairAdminAssignment saved = captor.getValue();
        assertThat(saved.getAdminUserId()).isEqualTo(NEW_ADMIN_ID);
        assertThat(saved.getRequesterUserId()).isEqualTo(APPLICANT_USER_ID);
        assertThat(saved.getFairId()).isEqualTo(FAIR_ID);
    }

    @Test
    void issueEventAdminAccount_정상발급시_managerEmail로_메일을보내고_새user_id를_반환한다() {
        givenHappyPath();

        Long result = adminAccountService.issueEventAdminAccount(
                FAIR_ID, APPLICANT_USER_ID, MANAGER_NAME, MANAGER_EMAIL, MANAGER_PHONE);

        assertThat(result).isEqualTo(NEW_ADMIN_ID);
        verify(mailService).sendAdminAccountIssueEmail(eq(MANAGER_EMAIL), anyString());
    }

    //신청자 중복(managerEmail 미존재) + insertUser 시 PK 생성까지 흉내내는 공통 스텁
    private void givenHappyPath() {
        given(authMapper.selectUserByEmail(MANAGER_EMAIL)).willReturn(null);
        given(authMapper.selectUserById(APPLICANT_USER_ID)).willReturn(applicant());
        given(passwordEncoder.encode(any())).willReturn("hashed-temp-password");
        //MyBatis useGeneratedKeys가 insertUser 이후 user.userId를 채워주는 걸 흉내
        given(authMapper.insertUser(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(NEW_ADMIN_ID);
            return 1;
        });
    }

    private User capturedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(authMapper).insertUser(captor.capture());
        return captor.getValue();
    }

    private User applicant() {
        return User.builder()
                .userId(APPLICANT_USER_ID)
                .email("applicant@petopia.com")
                .phone("010-1111-2222")
                .birthDate(LocalDate.of(1990, 5, 5))
                .gender("F")
                .address("서울시 강남구")
                .agreedTerms(true)
                .agreedPrivacy(true)
                .build();
    }
}
