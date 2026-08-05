package com.ms.petopia.api.auth.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = authMapper.selectUserById(userId);


        if(user == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())){
            throw new CommonException(ErrorCode.INVALID_PASSWORD);
        }
        String newHash = passwordEncoder.encode(newPassword);
        authMapper.updateUserPassword(userId, newHash);
    }


}
