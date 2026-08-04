package com.ms.petopia.api.auth.mapper;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.domain.UserToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {

    boolean existsVerifiedByEmail(@Param("email") String email);

    int insertUser(User user);

    int updateUnverifiedUser(User user);

    int insertUserToken(UserToken userToken);

    User selectUserByEmail(@Param("email") String email);

    //해당 유저의 아직 안 쓰인(무효화되지 않은) 최신 토큰 1건
    UserToken selectActiveUserToken(
            @Param("userId") Long userId,
            @Param("purpose") String purpose
    );

    //사용/폐기 여부와 상관없이 가장 최근 발급된 토큰 1건 - 재발급 쿨다운 판단용
    UserToken selectLatestToken(
            @Param("userId") Long userId,
            @Param("purpose") String purpose
    );

    //재전송 등으로 새 토큰을 발급하기 전 기존에 살아있던 토큰을 무효화
    int invalidateActiveTokens(
            @Param("userId") Long userId,
            @Param("purpose") String purpose
    );

    //maxAttempts 도달 시 같은 statement에서 토큰도 폐기
    int recordFailedAttempt(
            @Param("tokenId") Long tokenId,
            @Param("maxAttempts") int maxAttempts
    );

    int markUserTokenUsed(@Param("tokenId") Long tokenId);

    int markEmailVerified(@Param("userId") Long userId);
}
