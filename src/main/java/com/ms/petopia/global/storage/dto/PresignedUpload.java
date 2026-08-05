package com.ms.petopia.global.storage.dto;

/** Presigned PUT URL 발급 결과. URL은 로그에 기록하면 안 되는 민감한 값이다. */
public record PresignedUpload(
        String uploadUrl,
        String objectKey,
        long expiresInSeconds
) {
}
