package com.ms.petopia.api.auth.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

//fair_admin_assignments 테이블 row

@Getter
@Setter
@Builder
public class FairAdminAssignment {

    private Long fairAdminAssignmentId;
    private Long adminUserId;
    private Long requesterUserId;
    private Long fairId;
    private LocalDateTime assignedAt;
}
