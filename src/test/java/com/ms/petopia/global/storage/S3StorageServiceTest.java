package com.ms.petopia.global.storage;

import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.net.URI;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

class S3StorageServiceTest {

    private final S3Client s3Client = mock(S3Client.class);
    private final S3Presigner s3Presigner = mock(S3Presigner.class);
    private final StorageProperties properties = new StorageProperties(
            "test-bucket", "ap-northeast-2", "https://cdn.example", Duration.ofMinutes(5), "tmp/", "uploads/");
    private final ObjectKeyGenerator keyGenerator = new ObjectKeyGenerator(
            properties, Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC));
    private final S3StorageService storageService = new S3StorageService(
            s3Client, s3Presigner, properties, keyGenerator);

    @Test
    void presignPutReturnsTemporaryKeyAndExpiration() throws Exception {
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        given(s3Presigner.presignPutObject(
                org.mockito.ArgumentMatchers.<Consumer<PutObjectPresignRequest.Builder>>any()))
                .willReturn(presignedRequest);
        given(presignedRequest.url()).willReturn(URI.create("https://storage.example/upload").toURL());

        var upload = storageService.presignPut(UploadPolicy.IMAGE, "photo.png", "image/png", 10L);

        assertThat(upload.uploadUrl()).isEqualTo("https://storage.example/upload");
        assertThat(upload.objectKey()).startsWith("tmp/image/").endsWith(".png");
        assertThat(upload.expiresInSeconds()).isEqualTo(300L);
    }

    @Test
    void confirmCopiesThenDeletesValidatedTemporaryObject() {
        given(s3Client.headObject(any(HeadObjectRequest.class))).willReturn(
                HeadObjectResponse.builder().contentLength(10L).contentType("image/png").build());

        String finalKey = storageService.confirm(
                "tmp/image/550e8400-e29b-41d4-a716-446655440000.png", UploadPolicy.IMAGE);

        assertThat(finalKey).startsWith("uploads/image/2026/08/04/").endsWith(".png");
        InOrder order = inOrder(s3Client);
        order.verify(s3Client).headObject(any(HeadObjectRequest.class));
        order.verify(s3Client).copyObject(any(CopyObjectRequest.class));
        order.verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void confirmDeletesObjectWhenActualContentTypeDoesNotMatch() {
        given(s3Client.headObject(any(HeadObjectRequest.class))).willReturn(
                HeadObjectResponse.builder().contentLength(10L).contentType("image/jpeg").build());

        assertThatThrownBy(() -> storageService.confirm(
                "tmp/image/550e8400-e29b-41d4-a716-446655440000.png", UploadPolicy.IMAGE))
                .isInstanceOf(CommonException.class)
                .extracting(error -> ((CommonException) error).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_CONTENT_TYPE_MISMATCH);
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void confirmMapsMissingTemporaryObjectToDomainError() {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(404).build());

        assertThatThrownBy(() -> storageService.confirm(
                "tmp/image/550e8400-e29b-41d4-a716-446655440000.png", UploadPolicy.IMAGE))
                .isInstanceOf(CommonException.class)
                .extracting(error -> ((CommonException) error).getErrorCode())
                .isEqualTo(ErrorCode.STORAGE_UPLOAD_NOT_FOUND);
    }

    @Test
    void confirmSucceedsWhenTemporaryDeleteFailsAfterCopy() {
        given(s3Client.headObject(any(HeadObjectRequest.class))).willReturn(
                HeadObjectResponse.builder().contentLength(10L).contentType("image/png").build());
        doThrow(S3Exception.builder().statusCode(503).build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        String finalKey = storageService.confirm(
                "tmp/image/550e8400-e29b-41d4-a716-446655440000.png", UploadPolicy.IMAGE);

        assertThat(finalKey).startsWith("uploads/image/");
    }
}
