package com.ms.petopia.api.banner.dto.request;

import com.ms.petopia.api.banner.domain.Banner;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BannerUpdateRequest {
    @Size(max = 100)
    private String title;

    @Size(max = 50)
    private String eyebrow;

    @Size(max = 300)
    private String subtitle;

    @Size(max = 500)
    private String imageKey;

    @Size(max = 2000)
    private String linkUrl;

    private Banner.LinkTarget linkTarget;

    @Size(max = 50)
    private String linkLabel;

    @Size(max = 50)
    private String link2Label;

    @Size(max = 2000)
    private String link2Url;

    private Banner.LinkTarget link2Target;

    @Size(max = 10)
    private String bgColor;

    private Integer sortOrder;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
