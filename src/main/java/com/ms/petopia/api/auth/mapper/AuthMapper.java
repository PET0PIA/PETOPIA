package com.ms.petopia.api.auth.mapper;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.domain.UserToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {

    boolean existsByEmail(@Param("email") String email);

    int insertUser(User user);

    int insertUserToken(UserToken userToken);

    User selectUserByEmail(@Param("email") String email);

    UserToken selectUserTokenByHash(
            @Param("tokenHash") String tokenHash,
            @Param("purpose") String purpose
    );

    int markUserTokenUsed(@Param("tokenId") Long tokenId);

    int markEmailVerified(@Param("userId") Long userId);
}
