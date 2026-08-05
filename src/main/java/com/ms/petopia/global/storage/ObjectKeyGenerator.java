package com.ms.petopia.global.storage;

import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class ObjectKeyGenerator {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final StorageProperties properties;
    private final Clock clock;

    @Autowired
    public ObjectKeyGenerator(StorageProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ObjectKeyGenerator(StorageProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String extractExtension(String filename) {
        if (filename == null || filename.isBlank()
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new CommonException(ErrorCode.STORAGE_UNSUPPORTED_EXTENSION);
        }

        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 1 || lastDot == filename.length() - 1) {
            throw new CommonException(ErrorCode.STORAGE_UNSUPPORTED_EXTENSION);
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    public String newTemporaryKey(UploadPolicy policy, String extension) {
        policy.validateExtension(extension);
        return normalizedPrefix(properties.tmpPrefix())
                + policy.directory() + "/" + UUID.randomUUID() + "." + extension.toLowerCase(Locale.ROOT);
    }

    public String newConfirmedKey(UploadPolicy policy, String extension) {
        policy.validateExtension(extension);
        LocalDate today = LocalDate.now(clock);
        return normalizedPrefix(properties.confirmedPrefix())
                + policy.directory() + "/"
                + today.getYear() + "/%02d/%02d/".formatted(today.getMonthValue(), today.getDayOfMonth())
                + UUID.randomUUID() + "." + extension.toLowerCase(Locale.ROOT);
    }

    /** 유효한 tmp key에서 확장자를 돌려준다. */
    public String validateAndExtractTemporaryExtension(UploadPolicy policy, String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..")
                || objectKey.startsWith("/") || objectKey.contains("\\")) {
            throw new CommonException(ErrorCode.STORAGE_INVALID_OBJECT_KEY);
        }

        String expectedPrefix = normalizedPrefix(properties.tmpPrefix()) + policy.directory() + "/";
        if (!objectKey.startsWith(expectedPrefix)) {
            throw new CommonException(ErrorCode.STORAGE_INVALID_OBJECT_KEY);
        }

        String filename = objectKey.substring(expectedPrefix.length());
        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == filename.length() - 1 || filename.indexOf('/') >= 0) {
            throw new CommonException(ErrorCode.STORAGE_INVALID_OBJECT_KEY);
        }

        String uuid = filename.substring(0, lastDot);
        String extension = filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        if (!UUID_PATTERN.matcher(uuid).matches() || !policy.allowedExtensions().contains(extension)) {
            throw new CommonException(ErrorCode.STORAGE_INVALID_OBJECT_KEY);
        }
        return extension;
    }

    private String normalizedPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalStateException("스토리지 key prefix가 설정되지 않았습니다.");
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }
}
