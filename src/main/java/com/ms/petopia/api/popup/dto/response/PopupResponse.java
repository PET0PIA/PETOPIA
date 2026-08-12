package com.ms.petopia.api.popup.dto.response;

import com.ms.petopia.api.popup.domain.Popup;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class PopupResponse {

    private Long popupId;
    private String title;
    private String imageKey;
    private String linkUrl;
    private String linkTarget;
    private Integer width;
    private Integer height;
    private boolean isActive;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;

    public static PopupResponse from(Popup popup) {
        return PopupResponse.builder()
                .popupId(popup.getPopupId())
                .title(popup.getTitle())
                .imageKey(popup.getImageKey())
                .linkUrl(popup.getLinkUrl())
                .linkTarget(popup.getLinkTarget() != null ? popup.getLinkTarget().name() : null)
                .width(popup.getWidth())
                .height(popup.getHeight())
                .isActive(popup.isActive())
                .startedAt(popup.getStartedAt())
                .endedAt(popup.getEndedAt())
                .createdAt(popup.getCreatedAt())
                .build();
    }
}
