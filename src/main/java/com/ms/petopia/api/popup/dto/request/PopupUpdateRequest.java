package com.ms.petopia.api.popup.dto.request;

import com.ms.petopia.api.popup.domain.Popup;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class PopupUpdateRequest {

    @Size(max = 100)
    private String title;

    @Size(max = 1000)
    private String subtitle;

    @Size(max = 500)
    private String imageKey;

    @Size(max = 2000)
    private String linkUrl;

    private Popup.LinkTarget linkTarget;

    @Size(max = 50)
    private String linkLabel;

    @Size(max = 10)
    private String bgColor;

    @Positive
    private Integer width;

    @Positive
    private Integer height;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
