package com.ms.petopia.api.banner.domain;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner {
    private Long bannerId;
    private String title;
    private String imageKey;
    private String linkUrl;
    private LinkTarget linkTarget;
    private Integer sortOrder;
    private boolean isActive;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum LinkTarget {
        SELF, BLANK
    }
}
