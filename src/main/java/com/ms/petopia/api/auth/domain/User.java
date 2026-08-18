package com.ms.petopia.api.auth.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

//users 테이블 row

@Getter
@Setter
@Builder
public class User {

    private Long userId;
    private String email;
    private String passwordHash;
    private String nickname;
    private LocalDate birthDate;
    private String phone;
    private String gender;
    private String address;
    private boolean agreedTerms;
    private boolean agreedPrivacy;
    private String role;
    private String status;
    private boolean emailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
