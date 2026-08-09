package com.ms.petopia.api.auth.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

//AuthMapper.selectAdminAccounts()가 매핑하는 용도. users + fair_admin_assignments + fairs 조인 결과.
//Service에서 AdminAccountListItemResponse로 변환해서 내려줌
@Getter
@Setter
public class AdminAccountRow {
    private Long userId;
    private String email;
    private String nickname;
    private String status;
    private Long fairId;
    private String fairName;
    private LocalDate operationStartDate;
    private LocalDate operationEndDate;
}
