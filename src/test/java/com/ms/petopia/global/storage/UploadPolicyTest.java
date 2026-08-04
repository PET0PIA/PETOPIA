package com.ms.petopia.global.storage;

import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadPolicyTest {

    @Test
    void imagePolicyAllowsMatchingContentTypeAndMaximumSize() {
        assertThatCode(() -> {
            UploadPolicy.IMAGE.validateExtension("png");
            UploadPolicy.IMAGE.validateContentType("png", "IMAGE/PNG");
            UploadPolicy.IMAGE.validateSize(10L * 1024 * 1024);
        }).doesNotThrowAnyException();
    }

    @Test
    void imagePolicyRejectsMismatchedContentType() {
        assertThatThrownBy(() -> UploadPolicy.IMAGE.validateContentType("jpg", "image/png"))
                .isInstanceOf(CommonException.class)
                .extracting(error -> ((CommonException) error).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_CONTENT_TYPE_MISMATCH);
    }

    @Test
    void imagePolicyRejectsSizeOverLimit() {
        assertThatThrownBy(() -> UploadPolicy.IMAGE.validateSize(10L * 1024 * 1024 + 1))
                .isInstanceOf(CommonException.class)
                .extracting(error -> ((CommonException) error).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_FILE_TOO_LARGE);
    }
}
