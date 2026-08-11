package com.ms.petopia.api.popup.dto.request;

import com.ms.petopia.api.popup.domain.Popup;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class PopupUpdateRequest {

    @Size(max = 100)
    private String title;

    @Size(max = 500)
    private String imageKey;

    @Size(max = 2000)
    private String linkUrl;

    private Popup.LinkTarget linkTarget;
    private Integer width;
    private Integer height;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
