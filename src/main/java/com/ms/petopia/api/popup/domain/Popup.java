package com.ms.petopia.api.popup.domain;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Popup {
    private Long popupId;
    private String title;
    private String imageKey;
    private String linkUrl;
    private LinkTarget linkTarget;
    private Integer width;
    private Integer height;
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
