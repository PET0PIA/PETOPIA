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
    private String eyebrow;
    private String subtitle;
    private String imageKey;
    private String linkUrl;
    private String linkTarget;
    private String linkLabel;
    private String link2Label;
    private String link2Url;
    private String link2Target;
    private String bgColor;
    private int sortOrder;
    private boolean isActive;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;

    public static BannerResponse from(Banner banner){
        return BannerResponse.builder()
                .bannerId(banner.getBannerId())
                .title(banner.getTitle())
                .eyebrow(banner.getEyebrow())
                .subtitle(banner.getSubtitle())
                .imageKey(banner.getImageKey())
                .linkUrl(banner.getLinkUrl())
                .linkTarget(banner.getLinkTarget() != null ? banner.getLinkTarget().name() : null)
                .linkLabel(banner.getLinkLabel())
                .link2Label(banner.getLink2Label())
                .link2Url(banner.getLink2Url())
                .link2Target(banner.getLink2Target() != null ? banner.getLink2Target().name() : null)
                .bgColor(banner.getBgColor())
                .sortOrder(banner.getSortOrder())
                .isActive(banner.isActive())
                .startedAt(banner.getStartedAt())
                .endedAt(banner.getEndedAt())
                .createdAt(banner.getCreatedAt())
                .build();
    }
}
