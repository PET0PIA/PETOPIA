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
    private String eyebrow;
    private String subtitle;
    private String imageKey;
    private String linkUrl;
    private LinkTarget linkTarget;
    private String linkLabel;
    private String link2Label;
    private String link2Url;
    private LinkTarget link2Target;
    private String bgColor;
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
