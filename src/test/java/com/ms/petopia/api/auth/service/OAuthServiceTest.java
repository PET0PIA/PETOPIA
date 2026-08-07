package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.domain.UserSocialAccount;
import com.ms.petopia.api.auth.dto.OAuthAuthorizationStart;
import com.ms.petopia.api.auth.dto.OAuthSignupRequest;
import com.ms.petopia.api.auth.dto.OAuthUserInfo;
import com.ms.petopia.api.auth.dto.TokenPair;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.auth.mapper.UserSocialAccountMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.security.TokenHashUtil;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "test@petopia.com";
    private static final String FRONTEND_URL = "http://localhost:5173";
    private static final String PROVIDER = "GOOGLE";
    private static final String OAUTH_ID = "google-oauth-id-1";

    @Mock
    private OAuthProvider googleProvider;
    @Mock
    private OAuthStateStore oauthStateStore;
    @Mock
    private OAuthPendingStore oauthPendingStore;
    @Mock
    private UserSocialAccountMapper userSocialAccountMapper;
    @Mock
    private AuthMapper authMapper;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenStore refreshTokenStore;

    private OAuthService oAuthService;

    @BeforeEach
    void setUp() {
        //Map<String, OAuthProvider>는 @InjectMocks가 못 채워줘서 직접 생성자로 조립
        Map<String, OAuthProvider> oauthProviders = Map.of("google", googleProvider);
        oAuthService = new OAuthService(
                oauthProviders, oauthStateStore, oauthPendingStore,
                userSocialAccountMapper, authMapper, jwtTokenProvider, refreshTokenStore
        );
        //@Value 필드는 Spring이 주입하므로 테스트에서는 ReflectionTestUtils로 세팅
        ReflectionTestUtils.setField(oAuthService, "frontendUrl", FRONTEND_URL);
    }

    // ===== getAuthorizationUrl =====

    @Test
    void getAuthorizationUrl_state를생성해저장하고_provider가만든URL과state를함께반환한다() {
        given(googleProvider.getAuthorizationUrl(anyString())).willReturn("https://accounts.google.com/o/oauth2/v2/auth?...");

        OAuthAuthorizationStart result = oAuthService.getAuthorizationUrl("google");

        assertThat(result.url()).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth?...");
        assertThat(result.state()).isNotBlank();
        verify(oauthStateStore).save(eq("google"), eq(result.state()), eq(Duration.ofMinutes(5)));
        verify(googleProvider).getAuthorizationUrl(result.state());
    }

    @Test
    void getAuthorizationUrl_지원하지않는provider면_OAUTH_UNSUPPORTED_PROVIDER를던진다() {
        assertThatThrownBy(() -> oAuthService.getAuthorizationUrl("kakao"))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_UNSUPPORTED_PROVIDER);

        verify(oauthStateStore, never()).save(any(), any(), any());
    }

    // ===== handleCallback =====

    @Test
    void handleCallback_state가유효하지않으면_OAUTH_INVALID_STATE를던지고_구글에는묻지않는다() {
        given(oauthStateStore.validate("google", "bad-state")).willReturn(false);

        assertThatThrownBy(() -> oAuthService.handleCallback("google", "code", "bad-state"))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_INVALID_STATE);

        verify(googleProvider, never()).getUserInfo(any(), any());
    }

    @Test
    void handleCallback_이미연결된소셜계정이있으면_로그인핸드오프를저장하고login리다이렉트를반환한다() {
        given(oauthStateStore.validate("google", "state")).willReturn(true);
        given(googleProvider.getUserInfo("code", "state")).willReturn(new OAuthUserInfo(PROVIDER, OAUTH_ID, EMAIL, true));
        given(userSocialAccountMapper.selectByProviderAndOauthId(PROVIDER, OAUTH_ID))
                .willReturn(UserSocialAccount.builder().userId(USER_ID).build());

        String redirect = oAuthService.handleCallback("google", "code", "state");

        assertThat(redirect).startsWith(FRONTEND_URL + "/oauth/callback?type=login&code=");
        verify(oauthPendingStore).saveLogin(anyString(), eq(USER_ID), eq(Duration.ofMinutes(2)));
        //이미 연결돼 있으니 이메일로 재조회할 필요가 없음
        verify(authMapper, never()).selectUserByEmail(any());
    }

    @Test
    void handleCallback_소셜연결은없지만이메일검증된provider고이메일로가입된유저가있으면_자동연결하고login리다이렉트를반환한다() {
        given(oauthStateStore.validate("google", "state")).willReturn(true);
        given(googleProvider.getUserInfo("code", "state")).willReturn(new OAuthUserInfo(PROVIDER, OAUTH_ID, EMAIL, true));
        given(userSocialAccountMapper.selectByProviderAndOauthId(PROVIDER, OAUTH_ID)).willReturn(null);
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(User.builder().userId(USER_ID).email(EMAIL).build());

        String redirect = oAuthService.handleCallback("google", "code", "state");

        assertThat(redirect).startsWith(FRONTEND_URL + "/oauth/callback?type=login&code=");
        ArgumentCaptor<UserSocialAccount> captor = ArgumentCaptor.forClass(UserSocialAccount.class);
        verify(userSocialAccountMapper).insertSocialAccount(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getProvider()).isEqualTo(PROVIDER);
        assertThat(captor.getValue().getOauthId()).isEqualTo(OAUTH_ID);
        verify(oauthPendingStore).saveLogin(anyString(), eq(USER_ID), eq(Duration.ofMinutes(2)));
    }

    //네이버처럼 이메일 검증 클레임이 없는 provider는, 이메일이 같아도 자동연결하면 안 됨(코드래빗 리뷰 반영) -
    //남의 이메일을 자기 것처럼 적어낸 사람이 그 이메일의 진짜 주인 계정에 로그인돼버릴 수 있음
    @Test
    void handleCallback_이메일이일치해도emailVerified가false면_자동연결하지않고완전신규로처리한다() {
        given(oauthStateStore.validate("google", "state")).willReturn(true);
        given(googleProvider.getUserInfo("code", "state")).willReturn(new OAuthUserInfo(PROVIDER, OAUTH_ID, EMAIL, false));
        given(userSocialAccountMapper.selectByProviderAndOauthId(PROVIDER, OAUTH_ID)).willReturn(null);

        String redirect = oAuthService.handleCallback("google", "code", "state");

        assertThat(redirect).startsWith(FRONTEND_URL + "/oauth/callback?type=signup&code=");
        //이메일 검증이 안 됐으니 selectUserByEmail 자체를 호출하면 안 됨 - 자동연결 판단을 아예 안 함
        verify(authMapper, never()).selectUserByEmail(any());
        verify(userSocialAccountMapper, never()).insertSocialAccount(any());
        //emailVerified=false도 pending에 그대로 보존돼야 함 - completeSignup이 이 값을 users.email_verified에 씀
        verify(oauthPendingStore).saveSignup(anyString(), eq(EMAIL), eq(PROVIDER), eq(OAUTH_ID), eq(false), eq(Duration.ofMinutes(15)));
    }

    @Test
    void handleCallback_완전신규유저면_가입핸드오프를저장하고signup리다이렉트를반환한다() {
        given(oauthStateStore.validate("google", "state")).willReturn(true);
        given(googleProvider.getUserInfo("code", "state")).willReturn(new OAuthUserInfo(PROVIDER, OAUTH_ID, EMAIL, true));
        given(userSocialAccountMapper.selectByProviderAndOauthId(PROVIDER, OAUTH_ID)).willReturn(null);
        given(authMapper.selectUserByEmail(EMAIL)).willReturn(null);

        String redirect = oAuthService.handleCallback("google", "code", "state");

        assertThat(redirect).startsWith(FRONTEND_URL + "/oauth/callback?type=signup&code=");
        verify(oauthPendingStore).saveSignup(anyString(), eq(EMAIL), eq(PROVIDER), eq(OAUTH_ID), eq(true), eq(Duration.ofMinutes(15)));
        verify(oauthPendingStore, never()).saveLogin(any(), any(), any());
        verify(userSocialAccountMapper, never()).insertSocialAccount(any());
    }

    // ===== exchangeLogin =====

    @Test
    void exchangeLogin_유효한code면_토큰쌍을반환한다() {
        given(oauthPendingStore.consumeLogin("code")).willReturn(USER_ID);
        given(authMapper.selectUserById(USER_ID)).willReturn(User.builder().userId(USER_ID).role("USER").build());
        given(jwtTokenProvider.generateAccessToken(USER_ID, "USER")).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(USER_ID)).willReturn("refresh-token");

        TokenPair result = oAuthService.exchangeLogin("code");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenStore).save(eq(TokenHashUtil.sha256("refresh-token")), eq(USER_ID), eq(Duration.ofDays(14)));
    }

    @Test
    void exchangeLogin_핸드오프code가없으면_OAUTH_PENDING_NOT_FOUND를던진다() {
        given(oauthPendingStore.consumeLogin("bad-code")).willReturn(null);

        assertThatThrownBy(() -> oAuthService.exchangeLogin("bad-code"))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_PENDING_NOT_FOUND);

        verify(authMapper, never()).selectUserById(any());
    }

    @Test
    void exchangeLogin_userId에해당하는유저가없으면_OAUTH_PENDING_NOT_FOUND를던진다() {
        given(oauthPendingStore.consumeLogin("code")).willReturn(USER_ID);
        given(authMapper.selectUserById(USER_ID)).willReturn(null);

        assertThatThrownBy(() -> oAuthService.exchangeLogin("code"))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_PENDING_NOT_FOUND);

        verify(jwtTokenProvider, never()).generateAccessToken(any(), any());
    }

    // ===== completeSignup =====

    @Test
    void completeSignup_유효한tempKey면_유저와소셜계정을생성하고토큰쌍을반환한다() {
        OAuthPendingStore.OAuthPendingSignup pending =
                new OAuthPendingStore.OAuthPendingSignup(PROVIDER, OAUTH_ID, EMAIL, true);
        given(oauthPendingStore.peekSignup("temp-key")).willReturn(pending);
        //insertUser는 실제 DB에서는 useGeneratedKeys로 PK를 채워주는데, 목에서는 그 동작을 흉내내야 함
        willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(USER_ID);
            return 1;
        }).given(authMapper).insertUser(any(User.class));
        given(jwtTokenProvider.generateAccessToken(USER_ID, "USER")).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(USER_ID)).willReturn("refresh-token");

        TokenPair result = oAuthService.completeSignup(signupRequest("temp-key"));

        assertThat(result.accessToken()).isEqualTo("access-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(authMapper).insertUser(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(EMAIL);
        assertThat(savedUser.getPasswordHash()).isNull();
        assertThat(savedUser.getRole()).isEqualTo("USER");
        assertThat(savedUser.getStatus()).isEqualTo("ACTIVE");
        assertThat(savedUser.isEmailVerified()).isTrue();

        ArgumentCaptor<UserSocialAccount> socialCaptor = ArgumentCaptor.forClass(UserSocialAccount.class);
        verify(userSocialAccountMapper).insertSocialAccount(socialCaptor.capture());
        assertThat(socialCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(socialCaptor.getValue().getProvider()).isEqualTo(PROVIDER);
        assertThat(socialCaptor.getValue().getOauthId()).isEqualTo(OAUTH_ID);
        //성공했으니 pending 데이터를 지움 (단위테스트엔 실제 트랜잭션이 없어서 즉시 실행되는 경로를 탐)
        verify(oauthPendingStore).deleteSignup("temp-key");
    }

    @Test
    void completeSignup_tempKey가없으면_OAUTH_PENDING_NOT_FOUND를던지고_아무것도저장하지않는다() {
        given(oauthPendingStore.peekSignup("bad-key")).willReturn(null);

        assertThatThrownBy(() -> oAuthService.completeSignup(signupRequest("bad-key")))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_PENDING_NOT_FOUND);

        verify(authMapper, never()).insertUser(any());
        verify(userSocialAccountMapper, never()).insertSocialAccount(any());
    }

    //emailVerified=false인 provider(네이버 등)는 자동연결을 안 하다 보니, 이미 그 이메일로 가입된
    //유저가 있어도 여기까지 흘러들어올 수 있음 - UNIQUE 제약 위반을 500이 아니라 409로 응답해야 함
    @Test
    void completeSignup_이미가입된이메일이면_DUPLICATED_EMAIL을던지고_pending데이터를지우지않는다() {
        OAuthPendingStore.OAuthPendingSignup pending =
                new OAuthPendingStore.OAuthPendingSignup(PROVIDER, OAUTH_ID, EMAIL, true);
        given(oauthPendingStore.peekSignup("temp-key")).willReturn(pending);
        given(authMapper.insertUser(any(User.class))).willThrow(new DuplicateKeyException("email UNIQUE 위반"));

        assertThatThrownBy(() -> oAuthService.completeSignup(signupRequest("temp-key")))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATED_EMAIL);

        verify(userSocialAccountMapper, never()).insertSocialAccount(any());
        //DB insert가 실패했으니 pending은 그대로 남아있어야 함 - 재시도 가능하게
        verify(oauthPendingStore, never()).deleteSignup(any());
    }

    //네이버처럼 emailVerified=false로 저장된 pending이면, completeSignup도 그 값을 그대로 유지해야 함
    //(무조건 true로 확정하면 이메일의 진짜 주인이 나중에 일반가입을 못 하게 막히는 문제로 이어짐 - 코드래빗 리뷰 반영)
    @Test
    void completeSignup_pending의emailVerified가false면_유저도emailVerified가false로저장된다() {
        OAuthPendingStore.OAuthPendingSignup pending =
                new OAuthPendingStore.OAuthPendingSignup("NAVER", OAUTH_ID, EMAIL, false);
        given(oauthPendingStore.peekSignup("temp-key")).willReturn(pending);
        willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(USER_ID);
            return 1;
        }).given(authMapper).insertUser(any(User.class));
        given(jwtTokenProvider.generateAccessToken(USER_ID, "USER")).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(USER_ID)).willReturn("refresh-token");

        oAuthService.completeSignup(signupRequest("temp-key"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(authMapper).insertUser(userCaptor.capture());
        assertThat(userCaptor.getValue().isEmailVerified()).isFalse();
    }

    private OAuthSignupRequest signupRequest(String tempKey) {
        OAuthSignupRequest request = new OAuthSignupRequest();
        request.setTempKey(tempKey);
        request.setNickname("테스터");
        request.setBirthDate(LocalDate.of(1995, 1, 1));
        request.setPhone("01012345678");
        request.setGender("여성");
        request.setAddress("서울특별시 노원구");
        request.setAgreedTerms(true);
        request.setAgreedPrivacy(true);
        return request;
    }
}
