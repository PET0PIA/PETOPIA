package com.ms.petopia.api.auth.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class UserSocialAccount {

    private Long socialId;
    private Long userId;
    private String provider;
    private String oauthId;
    private LocalDateTime connectedAt;
}
