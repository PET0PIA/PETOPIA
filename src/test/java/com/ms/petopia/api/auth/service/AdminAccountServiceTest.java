package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.auth.domain.FairAdminAssignment;
import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.dto.AdminAccountListItemResponse;
import com.ms.petopia.api.auth.dto.AdminAccountRow;
import com.ms.petopia.api.auth.dto.EmailLoginRequest;
import com.ms.petopia.api.auth.dto.TokenPair;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.auth.mapper.FairAdminAssignmentMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

    private static final Long ADMIN_USER_ID = 1L;
    private static final String ADMIN_EMAIL = "admin@petopia.com";

    @Mock
    private AuthMapper authMapper;
    @Mock
    private FairAdminAssignmentMapper fairAdminAssignmentMapper;
    @Mock
    private MailService mailService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private AccountSuspensionStore accountSuspensionStore;
    @Mock
    private AuditLogService auditLogService;
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

    // ===== adminLogin =====

    @Test
    void adminLogin_SUPER_ADMIN이고_비밀번호가맞으면_토큰을발급한다() {
        given(authMapper.selectUserByEmail(ADMIN_EMAIL)).willReturn(superAdmin());
        given(passwordEncoder.matches("password1!", "hashed")).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(ADMIN_USER_ID, "SUPER_ADMIN")).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(ADMIN_USER_ID)).willReturn("refresh-token");

        TokenPair result = adminAccountService.adminLogin(loginRequest());

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenStore).save(any(String.class), eq(ADMIN_USER_ID), any());
    }

    @Test
    void adminLogin_이메일이없으면_INVALID_LOGIN을던진다() {
        given(authMapper.selectUserByEmail(ADMIN_EMAIL)).willReturn(null);

        assertThatThrownBy(() -> adminAccountService.adminLogin(loginRequest()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_LOGIN);

        verify(jwtTokenProvider, never()).generateAccessToken(anyLong(), any());
    }

    @Test
    void adminLogin_비밀번호가틀리면_INVALID_LOGIN을던진다() {
        given(authMapper.selectUserByEmail(ADMIN_EMAIL)).willReturn(superAdmin());
        given(passwordEncoder.matches("password1!", "hashed")).willReturn(false);

        assertThatThrownBy(() -> adminAccountService.adminLogin(loginRequest()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_LOGIN);
    }

    @Test
    void adminLogin_SUPER_ADMIN이아니면_ACCESS_DENIED를던진다() {
        User eventAdmin = User.builder()
                .userId(ADMIN_USER_ID)
                .email(ADMIN_EMAIL)
                .passwordHash("hashed")
                .role("EVENT_ADMIN")
                .status("ACTIVE")
                .build();
        given(authMapper.selectUserByEmail(ADMIN_EMAIL)).willReturn(eventAdmin);
        given(passwordEncoder.matches("password1!", "hashed")).willReturn(true);

        assertThatThrownBy(() -> adminAccountService.adminLogin(loginRequest()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(jwtTokenProvider, never()).generateAccessToken(anyLong(), any());
    }

    @Test
    void adminLogin_정지된계정이면_ACCOUNT_INACTIVE를던진다() {
        User inactiveAdmin = User.builder()
                .userId(ADMIN_USER_ID)
                .email(ADMIN_EMAIL)
                .passwordHash("hashed")
                .role("SUPER_ADMIN")
                .status("INACTIVE")
                .build();
        given(authMapper.selectUserByEmail(ADMIN_EMAIL)).willReturn(inactiveAdmin);
        given(passwordEncoder.matches("password1!", "hashed")).willReturn(true);

        assertThatThrownBy(() -> adminAccountService.adminLogin(loginRequest()))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_INACTIVE);

        verify(jwtTokenProvider, never()).generateAccessToken(anyLong(), any());
    }

    // ===== getAdminAccounts =====

    @Test
    void getAdminAccounts_Row를_ListItemResponse로_필드그대로_변환한다() {
        AdminAccountRow row = new AdminAccountRow();
        row.setUserId(ADMIN_USER_ID);
        row.setEmail(ADMIN_EMAIL);
        row.setNickname("김운영");
        row.setStatus("ACTIVE");
        row.setFairId(FAIR_ID);
        row.setFairName("펫페어 서울");
        row.setOperationStartDate(LocalDate.of(2026, 9, 1));
        row.setOperationEndDate(LocalDate.of(2026, 9, 3));
        given(authMapper.selectAdminAccounts()).willReturn(List.of(row));

        List<AdminAccountListItemResponse> result = adminAccountService.getAdminAccounts();

        assertThat(result).hasSize(1);
        AdminAccountListItemResponse response = result.get(0);
        assertThat(response.userId()).isEqualTo(ADMIN_USER_ID);
        assertThat(response.email()).isEqualTo(ADMIN_EMAIL);
        assertThat(response.nickname()).isEqualTo("김운영");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.fairName()).isEqualTo("펫페어 서울");
        assertThat(response.operationStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.operationEndDate()).isEqualTo(LocalDate.of(2026, 9, 3));
    }

    @Test
    void getAdminAccounts_결과가없으면_빈리스트를반환한다() {
        given(authMapper.selectAdminAccounts()).willReturn(List.of());

        List<AdminAccountListItemResponse> result = adminAccountService.getAdminAccounts();

        assertThat(result).isEmpty();
    }

    // ===== updateAccountStatus =====

    @Test
    void updateAccountStatus_INACTIVE로변경되면_Redis에정지를기록한다() {
        given(authMapper.updateUserStatus(ADMIN_USER_ID, "INACTIVE")).willReturn(1);

        adminAccountService.updateAccountStatus(ADMIN_USER_ID, "INACTIVE");

        verify(authMapper).updateUserStatus(ADMIN_USER_ID, "INACTIVE");
        verify(accountSuspensionStore).suspend(ADMIN_USER_ID);
        verify(accountSuspensionStore, never()).reactivate(any());
    }

    @Test
    void updateAccountStatus_ACTIVE로변경되면_Redis정지기록을지운다() {
        given(authMapper.updateUserStatus(ADMIN_USER_ID, "ACTIVE")).willReturn(1);

        adminAccountService.updateAccountStatus(ADMIN_USER_ID, "ACTIVE");

        verify(authMapper).updateUserStatus(ADMIN_USER_ID, "ACTIVE");
        verify(accountSuspensionStore).reactivate(ADMIN_USER_ID);
        verify(accountSuspensionStore, never()).suspend(any());
    }

    @Test
    void updateAccountStatus_대상유저가없으면_USER_NOT_FOUND를던지고_Redis는건드리지않는다() {
        given(authMapper.updateUserStatus(ADMIN_USER_ID, "INACTIVE")).willReturn(0);

        assertThatThrownBy(() -> adminAccountService.updateAccountStatus(ADMIN_USER_ID, "INACTIVE"))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(accountSuspensionStore, never()).suspend(any());
        verify(accountSuspensionStore, never()).reactivate(any());
    }

    private User superAdmin() {
        return User.builder()
                .userId(ADMIN_USER_ID)
                .email(ADMIN_EMAIL)
                .passwordHash("hashed")
                .role("SUPER_ADMIN")
                .status("ACTIVE")
                .build();
    }

    private EmailLoginRequest loginRequest() {
        EmailLoginRequest request = new EmailLoginRequest();
        request.setEmail(ADMIN_EMAIL);
        request.setPassword("password1!");
        return request;
    }
}
