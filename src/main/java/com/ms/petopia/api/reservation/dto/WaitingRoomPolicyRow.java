package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class WaitingRoomPolicyRow {
    private Long fairId;
    private boolean enabled;
    private int activeLimit;
    private int version;
    private LocalDateTime updatedAt;
}
