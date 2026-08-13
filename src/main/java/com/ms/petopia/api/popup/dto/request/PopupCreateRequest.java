package com.ms.petopia.api.popup.dto.request;

import com.ms.petopia.api.popup.domain.Popup;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class PopupCreateRequest {

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 1000)
    private String subtitle;

    @Size(max = 500)
    private String imageKey;

    @Size(max = 2000)
    private String linkUrl;

    @NotNull
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

    @AssertTrue(message = "imageKey 또는 subtitle 중 하나는 있어야 합니다.")
    public boolean isContentValid() {
        return (imageKey != null && !imageKey.isBlank()) || (subtitle != null && !subtitle.isBlank());
    }

    @AssertTrue(message = "linkLabel을 설정하려면 linkUrl도 함께 설정해야 합니다.")
    public boolean isLinkValid() {
        return linkLabel == null || linkUrl != null;
    }
}
