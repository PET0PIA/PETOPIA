package com.ms.petopia.global.storage;

import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectKeyGeneratorTest {

    private final ObjectKeyGenerator generator = new ObjectKeyGenerator(
            new StorageProperties("bucket", "ap-northeast-2", "https://cdn.example", null, "tmp/", "uploads/"),
            Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void createsTemporaryAndConfirmedKeysWithExpectedPrefixes() {
        String temporaryKey = generator.newTemporaryKey(UploadPolicy.IMAGE, generator.extractExtension("PHOTO.PNG"));
        String confirmedKey = generator.newConfirmedKey(UploadPolicy.IMAGE, "png");

        assertThat(temporaryKey).startsWith("tmp/image/").endsWith(".png");
        assertThat(confirmedKey).startsWith("uploads/image/2026/08/04/").endsWith(".png");
    }

    @Test
    void validatesTemporaryKeyAndExtractsExtension() {
        String extension = generator.validateAndExtractTemporaryExtension(
                UploadPolicy.IMAGE, "tmp/image/550e8400-e29b-41d4-a716-446655440000.png");

        assertThat(extension).isEqualTo("png");
    }

    @Test
    void rejectsTraversalAndMalformedTemporaryKey() {
        assertThatThrownBy(() -> generator.extractExtension("../photo.png"))
                .isInstanceOf(CommonException.class)
                .extracting(error -> ((CommonException) error).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_UNSUPPORTED_EXTENSION);
        assertThatThrownBy(() -> generator.extractExtension("folder\\photo.png"))
                .isInstanceOf(CommonException.class)
                .extracting(error -> ((CommonException) error).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_UNSUPPORTED_EXTENSION);
        assertThatThrownBy(() -> generator.validateAndExtractTemporaryExtension(
                UploadPolicy.IMAGE, "tmp/image/not-a-uuid.png"))
                .isInstanceOf(CommonException.class)
                .extracting(error -> ((CommonException) error).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_INVALID_OBJECT_KEY);
    }
}
