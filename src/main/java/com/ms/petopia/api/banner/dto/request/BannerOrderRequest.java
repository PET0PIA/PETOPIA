package com.ms.petopia.api.banner.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BannerOrderRequest {
    @NotNull
    private List<@NotNull Long> bannerIds; // 순서대로 bannerId 목록
}
