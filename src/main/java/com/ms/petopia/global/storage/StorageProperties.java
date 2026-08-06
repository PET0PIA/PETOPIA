package com.ms.petopia.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("petopia.storage")
public record StorageProperties(
        String bucket,
        String region,
        String publicBaseUrl,
        Duration presignExpiry,
        String tmpPrefix,
        String confirmedPrefix
) {
}
