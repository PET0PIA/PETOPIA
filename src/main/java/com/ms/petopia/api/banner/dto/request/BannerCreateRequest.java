package com.ms.petopia.api.banner.dto.request;

import com.ms.petopia.api.banner.domain.Banner;
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

    @NotBlank
    @Size(max = 500)
    private String imageKey;

    @Size(max = 2000)
    private String linkUrl;

    @NotNull
    private Banner.LinkTarget linkTarget;

    @NotNull
    private Integer sortOrder;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
