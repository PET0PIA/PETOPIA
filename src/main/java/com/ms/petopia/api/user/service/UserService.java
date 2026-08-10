package com.ms.petopia.api.user.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.user.dto.UserMeResponse;
import com.ms.petopia.api.user.dto.UserUpdateRequest;
import com.ms.petopia.api.user.mapper.UserMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public UserMeResponse getMe(Long userId) {
        User user = userMapper.selectUserById(userId);
        if (user == null) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
        return toResponse(user);
    }

    //부분 수정. null이 아닌 것만 반영
    @Transactional
    public UserMeResponse updateMe(Long userId, UserUpdateRequest request) {
        //전부 null이면 SET절이 비는 것도 방지
        boolean hasAnyField = request.getNickname() != null
                || request.getBirthDate() != null
                || request.getPhone() != null
                || request.getGender() != null
                || request.getAddress() != null;

        if (hasAnyField) {
            int updated = userMapper.updateUserProfile(
                    userId,
                    request.getNickname(),
                    request.getBirthDate(),
                    request.getPhone(),
                    request.getGender(),
                    request.getAddress()
            );
            if (updated == 0) {
                throw new CommonException(ErrorCode.USER_NOT_FOUND);
            }
        }

        return getMe(userId);
    }

    private UserMeResponse toResponse(User user) {
        return new UserMeResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getBirthDate(),
                user.getPhone(),
                user.getGender(),
                user.getAddress(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}
