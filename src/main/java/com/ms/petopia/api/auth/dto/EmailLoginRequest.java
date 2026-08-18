package com.ms.petopia.api.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;


@Getter
@Setter
@NoArgsConstructor
public class EmailLoginRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

}
