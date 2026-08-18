package com.ms.petopia.api.user.mapper;

import com.ms.petopia.api.auth.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface UserMapper {

    User selectUserById(@Param("userId") Long userId);

    //부분 수정. null인 필드는 SQL의 <if>에서 걸러져 업데이트 대상에서 빠진다
    int updateUserProfile(
            @Param("userId") Long userId,
            @Param("nickname") String nickname,
            @Param("birthDate") LocalDate birthDate,
            @Param("phone") String phone,
            @Param("gender") String gender,
            @Param("address") String address
    );
}
