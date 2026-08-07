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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OAuthService {

    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private static final Duration LOGIN_PENDING_TTL = Duration.ofMinutes(2);
    private static final Duration SIGNUP_PENDING_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

    //Spring이 OAuthProvider 구현체 빈들을 빈 이름 -> 빈 객체로 모아서 주입해줌.
    //GoogleOAuthProvider가 @Component("google")이라 여기선 oauthProviders.get("google")로 찾음
    private final Map<String, OAuthProvider> oauthProviders;
    private final OAuthStateStore oauthStateStore;
    private final OAuthPendingStore oauthPendingStore;
    private final UserSocialAccountMapper userSocialAccountMapper;
    private final AuthMapper authMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    //state와 provider 연결
    //state도 같이 리턴하는 이유: 컨트롤러가 이 값을 브라우저 바인딩용 상관관계 쿠키에 담아야 하기 때문
    //로그인 CSRF 방지 state가 Redis에 있다는 것만으로는 그 브라우저가 맞는지까지는 증명 못 함
    public OAuthAuthorizationStart getAuthorizationUrl(String provider) {
        //provider 검증을 먼저 해서, 잘못된 provider일 땐 state를 만들지도 저장하지도 않게 함
        OAuthProvider oauthProvider = resolveProvider(provider);
        String state = TokenHashUtil.generateRawToken();
        oauthStateStore.save(provider, state, STATE_TTL);
        String url = oauthProvider.getAuthorizationUrl(state);
        return new OAuthAuthorizationStart(url, state);
    }

    //callback을 받았을 때의 메소드
    //리턴값은 프론트로 리다이렉트시킬 최종 URL 문자열.
    public String handleCallback(String provider, String code, String state) {
        if(!oauthStateStore.validate(provider, state)) {
            throw new CommonException(ErrorCode.OAUTH_INVALID_STATE);
        }
        OAuthUserInfo userInfo = resolveProvider(provider).getUserInfo(code, state);   //(provider, oauthId, email)
        Long existingUserId = findExistingUserId(userInfo);   // 있으면 userId, 없으면

        if (existingUserId != null) {
            // 기존 유저
            String loginCode = TokenHashUtil.generateRawToken();
            oauthPendingStore.saveLogin(loginCode, existingUserId, LOGIN_PENDING_TTL);
            return frontendUrl + "/oauth/callback?type=login&code=" + loginCode;
        }

        // 신규 유저
        String tempKey = TokenHashUtil.generateRawToken();
        oauthPendingStore.saveSignup(tempKey, userInfo.email(), userInfo.provider(), userInfo.oauthId(), SIGNUP_PENDING_TTL);
        return frontendUrl + "/oauth/callback?type=signup&code=" + tempKey;

    }

    //기존 유저 로그인 마무리
    public TokenPair exchangeLogin(String code) {
        //consumeLogin은 GETDEL이라 한 번만 호출 - 두 번 부르면 두 번째는 항상 null
        Long userId = oauthPendingStore.consumeLogin(code);
        if (userId == null) {
            throw new CommonException(ErrorCode.OAUTH_PENDING_NOT_FOUND);
        }

        User user = authMapper.selectUserById(userId);
        if (user == null) {
            throw new CommonException(ErrorCode.OAUTH_PENDING_NOT_FOUND);
        }

        return issueTokens(user.getUserId(), user.getRole());
    }

    //신규 유저 가입 마무리
    @Transactional
    public TokenPair completeSignup(OAuthSignupRequest request) {
        //GETDEL 대신 비파괴적 조회(peekSignup)
        //DB 실패로 롤백되더라도 이 pending 데이터는 Redis에 그대로 남아있어야 유저가 로그인부터 다시 안 하고 같은 tempKey로 재시도할 수 있음
        String tempKey = request.getTempKey();
        OAuthPendingStore.OAuthPendingSignup pending = oauthPendingStore.peekSignup(tempKey);
        if (pending == null) {
            throw new CommonException(ErrorCode.OAUTH_PENDING_NOT_FOUND);
        }

        User user = User.builder()
                .email(pending.email())           //tempKey가 아니라 Redis에서 꺼낸 진짜 이메일
                .passwordHash(null)                //OAuth 유저는 비밀번호 없음
                .nickname(request.getNickname())
                .birthDate(request.getBirthDate())
                .phone(request.getPhone().replaceAll("[^0-9]", ""))
                .gender(request.getGender())
                .address(request.getAddress())
                .agreedTerms(request.getAgreedTerms())
                .agreedPrivacy(request.getAgreedPrivacy())
                .role("USER")
                .status("ACTIVE")
                .emailVerified(true)
                .createdAt(LocalDateTime.now())
                .build();

        //emailVerified가 false인 네이버는 자동연결을 안 하다 보니
        //이미 가입된 유저가 있어도 completeSignup까지 흘러들어올 수 있음 UNIQUE 제약 위반을 500 대신 409로 변환
        try {
            authMapper.insertUser(user);   //useGeneratedKeys라 insert 후 user.getUserId()에 PK가 채워짐
        } catch (DuplicateKeyException e) {
            throw new CommonException(ErrorCode.DUPLICATED_EMAIL, e);
        }

        UserSocialAccount socialAccount = UserSocialAccount.builder()
                .userId(user.getUserId())
                .provider(pending.provider())
                .oauthId(pending.oauthId())
                .connectedAt(LocalDateTime.now())
                .build();
        userSocialAccountMapper.insertSocialAccount(socialAccount);

        //DB 커밋이 성공한 뒤에만 Redis pending 데이터를 지운다
        deferOrRunNow(() -> oauthPendingStore.deleteSignup(tempKey));

        return issueTokens(user.getUserId(), user.getRole());
    }



    //{provider} 문자열로 실제 OAuthProvider 구현체 찾기. 없으면(오타/미지원 provider) 에러
    private OAuthProvider resolveProvider(String provider) {
        OAuthProvider oauthProvider = oauthProviders.get(provider.toLowerCase());
        if (oauthProvider == null) {
            throw new CommonException(ErrorCode.OAUTH_UNSUPPORTED_PROVIDER);
        }
        return oauthProvider;
    }

    //JWT 만들고 refreshToken을 해시로 저장.
    //completeSignup(@Transactional) 안에서 불릴 땐 저장을 커밋 이후로 미룸 - 안 그러면 DB 커밋이
    //마지막 순간에 실패했을 때 "DB엔 없는 유저의 refreshToken"이 Redis에 고아로 남을 수 있음.
    //exchangeLogin(트랜잭션 없음)에서 불릴 땐 어차피 DB write가 없어서 바로 저장해도 문제없음
    private TokenPair issueTokens(Long userId, String role) {
        String accessToken = jwtTokenProvider.generateAccessToken(userId, role);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);
        String refreshTokenHash = TokenHashUtil.sha256(refreshToken);

        deferOrRunNow(() -> refreshTokenStore.save(refreshTokenHash, userId, REFRESH_TOKEN_TTL));

        return new TokenPair(accessToken, refreshToken);
    }

    //현재 진행 중인 @Transactional이 있으면 그 커밋 성공 후로 실행을 미루고
    //없으면 즉시 실행한다
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

    //기존 유저면 userId, 완전 신규면 null 리턴
    private Long findExistingUserId(OAuthUserInfo userInfo) {
        UserSocialAccount socialAccount =
                userSocialAccountMapper.selectByProviderAndOauthId(userInfo.provider(), userInfo.oauthId());
        if (socialAccount != null) {
            //이미 연결된 계정 있음 이메일 신뢰도랑 무관하게 항상 허용
            return socialAccount.getUserId();
        }

        //검증 안 된 이메일(네이버)로 자동 연결하면, 남의 이메일을 자기 것처럼 적어낸 사람이
        //그 이메일의 진짜 주인 계정에 로그인돼버릴 위험이 있어 완전 신규로 취급
        if (!userInfo.emailVerified()) {
            return null;
        }

        User existingUser = authMapper.selectUserByEmail(userInfo.email());
        if (existingUser != null) {
            //이메일로는 가입돼 있었음. 소셜 계정만 새로 연결
            UserSocialAccount newLink = UserSocialAccount.builder()
                    .userId(existingUser.getUserId())
                    .provider(userInfo.provider())
                    .oauthId(userInfo.oauthId())
                    .connectedAt(LocalDateTime.now())
                    .build();
            userSocialAccountMapper.insertSocialAccount(newLink);
            return existingUser.getUserId();
        }

        return null;   //완전 신규
    }
}
