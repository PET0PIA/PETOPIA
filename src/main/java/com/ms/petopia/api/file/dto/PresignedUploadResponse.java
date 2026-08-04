package com.ms.petopia.api.file.dto;

public record PresignedUploadResponse(
        String uploadUrl,
        String objectKey,
        long expiresInSeconds
) {
}
