package com.ms.petopia.api.auth.mapper;

import com.ms.petopia.api.auth.domain.UserSocialAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserSocialAccountMapper {

    //OAuth 콜백에서 (provider, oauthId)로 기존 연결 조회
    UserSocialAccount selectByProviderAndOauthId(
            @Param("provider") String provider,
            @Param("oauthId") String oauthId
    );

    //신규 가입 완료 시 소셜 계정 연결 저장
    int insertSocialAccount(UserSocialAccount userSocialAccount);
}
