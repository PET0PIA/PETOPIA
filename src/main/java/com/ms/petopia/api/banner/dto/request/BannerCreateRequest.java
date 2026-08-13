package com.ms.petopia.api.banner.dto.request;

import com.ms.petopia.api.banner.domain.Banner;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BannerCreateRequest {
    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 50)
    private String eyebrow;

    @Size(max = 300)
    private String subtitle;

    @NotBlank
    @Size(max = 500)
    private String imageKey;

    @Size(max = 2000)
    private String linkUrl;

    @NotNull
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

    @NotNull
    private Integer sortOrder;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    @AssertTrue(message = "linkLabel을 설정하려면 linkUrl도 함께 설정해야 합니다.")
    public boolean isLinkValid() {
        return linkLabel == null || linkUrl != null;
    }

    @AssertTrue(message = "link2Label과 link2Url은 둘 다 설정하거나 둘 다 비워야 합니다.")
    public boolean isLink2Valid() {
        return (link2Label == null) == (link2Url == null);
    }
}
