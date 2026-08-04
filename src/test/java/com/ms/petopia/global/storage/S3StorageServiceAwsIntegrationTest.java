package com.ms.petopia.global.storage;

import com.ms.petopia.global.storage.dto.PresignedUpload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 AWS S3에 파일을 올리는 통합 테스트다.
 *
 * <p>기본 테스트에서는 실행되지 않는다. AWS 자격 증명(DefaultCredentialsProvider)과 함께 아래처럼 실행한다.
 * <pre>
 * RUN_AWS_S3_INTEGRATION_TEST=true S3_BUCKET=개발용-버킷 ./gradlew test \
 *   --tests com.ms.petopia.global.storage.S3StorageServiceAwsIntegrationTest
 * </pre>
 * 운영 버킷이 아닌, 테스트 전용 버킷을 지정해야 한다.
 */
@Tag("aws-integration")
@EnabledIfEnvironmentVariable(named = "RUN_AWS_S3_INTEGRATION_TEST", matches = "true")
class S3StorageServiceAwsIntegrationTest {

    private static final String CONTENT_TYPE = "image/png";
    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL9nQAAAABJRU5ErkJggg==");

    private final String bucket = requiredEnvironmentVariable("S3_BUCKET");
    private final StorageProperties properties = new StorageProperties(
            bucket,
            System.getenv().getOrDefault("AWS_REGION", "ap-northeast-2"),
            "https://unused.example",
            Duration.ofMinutes(5),
            "tmp/",
            "uploads/");
    private final S3Config s3Config = new S3Config();
    private final S3Client s3Client = s3Config.s3Client(properties);
    private final S3Presigner s3Presigner = s3Config.s3Presigner(properties);
    private final S3StorageService storageService = new S3StorageService(
            s3Client, s3Presigner, properties, new ObjectKeyGenerator(properties));

    private String temporaryKey;
    private String confirmedKey;

    @Test
    @DisplayName("presigned URL로 실제 S3에 업로드하고 확정 파일로 이동한다")
    void uploadsToAwsS3WithPresignedUrlAndConfirmsObject() throws Exception {
        PresignedUpload upload = storageService.presignPut(
                UploadPolicy.IMAGE, "aws-integration-test.png", CONTENT_TYPE, PNG_BYTES.length);
        temporaryKey = upload.objectKey();

        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(upload.uploadUrl()))
                        .header("Content-Type", CONTENT_TYPE)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(PNG_BYTES))
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(s3Client.headObject(HeadObjectRequest.builder()
                        .bucket(bucket)
                        .key(temporaryKey)
                        .build()))
                .extracting(head -> head.contentLength(), head -> head.contentType())
                .containsExactly((long) PNG_BYTES.length, CONTENT_TYPE);

        confirmedKey = storageService.confirm(temporaryKey, UploadPolicy.IMAGE);
        assertThat(confirmedKey).startsWith("uploads/image/").endsWith(".png");
        assertThat(s3Client.headObject(HeadObjectRequest.builder()
                        .bucket(bucket)
                        .key(confirmedKey)
                        .build()))
                .extracting(head -> head.contentLength(), head -> head.contentType())
                .containsExactly((long) PNG_BYTES.length, CONTENT_TYPE);
    }

    @AfterEach
    void cleanUpUploadedObjects() {
        deleteIfPresent(temporaryKey);
        deleteIfPresent(confirmedKey);
    }

    private void deleteIfPresent(String key) {
        if (key != null) {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required for the AWS S3 integration test.");
        }
        return value;
    }
}
