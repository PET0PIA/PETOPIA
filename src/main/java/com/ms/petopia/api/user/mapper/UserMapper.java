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

    //탈퇴 처리: status/deleted_at 전환. email을 합성값으로 치환하고 원래 이메일은 withdrawnEmail에 보존.
    //deleted_at IS NULL 조건으로 동시 탈퇴 요청 중 하나만 반영되게 막는다
    int withdrawUser(
            @Param("userId") Long userId,
            @Param("syntheticEmail") String syntheticEmail,
            @Param("withdrawnEmail") String withdrawnEmail
    );
}
