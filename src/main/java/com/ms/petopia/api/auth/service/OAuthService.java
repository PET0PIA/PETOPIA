package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.domain.UserSocialAccount;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public String getAuthorizationUrl(String provider) {
        //provider 검증을 먼저 해서, 잘못된 provider일 땐 state를 만들지도 저장하지도 않게 함
        OAuthProvider oauthProvider = resolveProvider(provider);
        String state = TokenHashUtil.generateRawToken();
        oauthStateStore.save(state, STATE_TTL);
        return oauthProvider.getAuthorizationUrl(state);
    }

    //callback을 받았을 때의 메소드
    //리턴값은 프론트로 리다이렉트시킬 최종 URL 문자열.
    public String handleCallback(String provider, String code, String state) {
        if(!oauthStateStore.validate(state)) {
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
        //consumeSignup도 GETDEL이라 한 번만 호출
        OAuthPendingStore.OAuthPendingSignup pending = oauthPendingStore.consumeSignup(request.getTempKey());
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

        authMapper.insertUser(user);   //useGeneratedKeys라 insert 후 user.getUserId()에 PK가 채워짐

        UserSocialAccount socialAccount = UserSocialAccount.builder()
                .userId(user.getUserId())
                .provider(pending.provider())
                .oauthId(pending.oauthId())
                .connectedAt(LocalDateTime.now())
                .build();
        userSocialAccountMapper.insertSocialAccount(socialAccount);

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

    //JWT 만들고 refreshToken을 해시로 저장
    private TokenPair issueTokens(Long userId, String role) {
        String accessToken = jwtTokenProvider.generateAccessToken(userId, role);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        String refreshTokenHash = TokenHashUtil.sha256(refreshToken);
        refreshTokenStore.save(refreshTokenHash, userId, REFRESH_TOKEN_TTL);

        return new TokenPair(accessToken, refreshToken);
    }

    //기존 유저면 userId, 완전 신규면 null 리턴
    private Long findExistingUserId(OAuthUserInfo userInfo) {
        UserSocialAccount socialAccount =
                userSocialAccountMapper.selectByProviderAndOauthId(userInfo.provider(), userInfo.oauthId());
        if (socialAccount != null) {
            return socialAccount.getUserId();   //이미 연결된 계정 있음
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
