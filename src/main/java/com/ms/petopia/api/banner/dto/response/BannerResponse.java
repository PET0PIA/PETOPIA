package com.ms.petopia.api.banner.dto.response;

import com.ms.petopia.api.banner.domain.Banner;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BannerResponse {
    private Long bannerId;
    private String title;
    private String imageKey;
    private String linkUrl;
    private String linkTarget;
    private int sortOrder;
    private boolean isActive;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;

    public static BannerResponse from(Banner banner){
        return BannerResponse.builder()
                .bannerId(banner.getBannerId())
                .title(banner.getTitle())
                .imageKey(banner.getImageKey())
                .linkUrl(banner.getLinkUrl())
                .linkTarget(banner.getLinkTarget() != null ? banner.getLinkTarget().name() : null)
                .sortOrder(banner.getSortOrder())
                .isActive(banner.isActive())
                .startedAt(banner.getStartedAt())
                .endedAt(banner.getEndedAt())
                .createdAt(banner.getCreatedAt())
                .build();
    }
}
