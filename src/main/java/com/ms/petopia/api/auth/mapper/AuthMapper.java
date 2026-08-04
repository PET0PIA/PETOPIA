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

    //refresh 시 최신 role 조회용 - 쓰기가 없으니 락 안 걺
    User selectUserById(@Param("userId") Long userId);

    //재전송 동시 요청을 직렬화하기 위한 행 잠금 조회 - user_tokens는 신규 유저에게 아직 없을 수 있어 users 행을 잠금
    User selectUserByIdForUpdate(@Param("userId") Long userId);

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

    //인증 완료 기록
    int markEmailVerified(@Param("userId") Long userId);

    //내부 연동: 사업자 등록/삭제 시 role 부여·회수 (참가업체·부스 도메인이 호출)
    int updateUserRole(@Param("userId") Long userId, @Param("role") String role);
}
