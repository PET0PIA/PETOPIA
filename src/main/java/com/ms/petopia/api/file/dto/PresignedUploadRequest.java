package com.ms.petopia.api.file.dto;

import com.ms.petopia.global.storage.UploadPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PresignedUploadRequest(
        @NotNull UploadPolicy policy,
        @NotBlank @Size(max = 255) String filename,
        @NotBlank String contentType,
        @Positive long size
) {
}
