package com.ms.petopia.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminAccountStatusUpdateRequest {

    //ACTIVE/INACTIVE 외 다른 값으로 users.status가 바뀌는 걸 막기 위해 패턴으로 제한
    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE", message = "status는 ACTIVE 또는 INACTIVE만 가능합니다.")
    private String status;

}
