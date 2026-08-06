package com.ms.petopia.global.storage;

import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 도메인과 무관하게 재사용하는 업로드 파일 정책. */
public enum UploadPolicy {

    IMAGE("image", Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    ), 10L * 1024 * 1024),

    DOCUMENT("document", Map.of(
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    ), 50L * 1024 * 1024);

    private final String directory;
    private final Map<String, String> contentTypesByExtension;
    private final long maxBytes;

    UploadPolicy(String directory, Map<String, String> contentTypesByExtension, long maxBytes) {
        this.directory = directory;
        this.contentTypesByExtension = contentTypesByExtension;
        this.maxBytes = maxBytes;
    }

    public String directory() {
        return directory;
    }

    public Set<String> allowedExtensions() {
        return contentTypesByExtension.keySet();
    }

    public long maxBytes() {
        return maxBytes;
    }

    public void validateExtension(String extension) {
        if (!contentTypesByExtension.containsKey(normalize(extension))) {
            throw new CommonException(ErrorCode.STORAGE_UNSUPPORTED_EXTENSION);
        }
    }

    public void validateContentType(String extension, String contentType) {
        String expected = contentTypesByExtension.get(normalize(extension));
        if (expected == null || !expected.equals(normalize(contentType))) {
            throw new CommonException(ErrorCode.STORAGE_CONTENT_TYPE_MISMATCH);
        }
    }

    public void validateSize(long size) {
        if (size <= 0 || size > maxBytes) {
            throw new CommonException(ErrorCode.STORAGE_FILE_TOO_LARGE);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
