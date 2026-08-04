package com.ms.petopia.api.auth.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

//user_tokens 테이블 row

@Getter
@Setter
@Builder
public class UserToken {

    private Long tokenId;
    private Long userId;
    private String tokenHash;
    private String purpose;
    private LocalDateTime usedAt;
    private int attemptCount;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
