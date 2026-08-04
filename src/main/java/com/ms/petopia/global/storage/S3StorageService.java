package com.ms.petopia.global.storage;

import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.dto.PresignedUpload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;
    private final ObjectKeyGenerator objectKeyGenerator;

    @Override
    public PresignedUpload presignPut(UploadPolicy policy, String filename, String contentType, long size) {
        String extension = objectKeyGenerator.extractExtension(filename);
        policy.validateExtension(extension);
        policy.validateContentType(extension, contentType);
        policy.validateSize(size);

        String temporaryKey = objectKeyGenerator.newTemporaryKey(policy, extension);
        try {
            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(builder -> builder
                    .signatureDuration(properties.presignExpiry())
                    .putObjectRequest(request -> request
                            .bucket(properties.bucket())
                            .key(temporaryKey)
                            .contentType(contentType)));

            return new PresignedUpload(
                    presignedRequest.url().toString(),
                    temporaryKey,
                    properties.presignExpiry().toSeconds());
        } catch (SdkClientException e) {
            throw storageUnavailable(e);
        }
    }

    @Override
    public String confirm(String temporaryObjectKey, UploadPolicy policy) {
        String extension = objectKeyGenerator.validateAndExtractTemporaryExtension(policy, temporaryObjectKey);
        HeadObjectResponse headObject = headObject(temporaryObjectKey);
        String sourceETag = requiredETag(temporaryObjectKey, headObject);

        try {
            policy.validateSize(headObject.contentLength());
            policy.validateContentType(extension, headObject.contentType());
        } catch (CommonException e) {
            deleteIgnoringFailure(temporaryObjectKey, sourceETag);
            throw e;
        }

        String confirmedKey = objectKeyGenerator.newConfirmedKey(policy, extension);
        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(properties.bucket())
                    .sourceKey(temporaryObjectKey)
                    .copySourceIfMatch(sourceETag)
                    .destinationBucket(properties.bucket())
                    .destinationKey(confirmedKey)
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == 409 || e.statusCode() == 412) {
                throw new CommonException(ErrorCode.STORAGE_UPLOAD_CHANGED);
            }
            throw storageUnavailable(e);
        } catch (SdkClientException e) {
            throw storageUnavailable(e);
        }

        deleteIgnoringFailure(temporaryObjectKey, sourceETag);
        return confirmedKey;
    }

    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception | SdkClientException e) {
            throw storageUnavailable(e);
        }
    }

    @Override
    public String toPublicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            return objectKey;
        }
        String baseUrl = properties.publicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new CommonException(ErrorCode.STORAGE_UNAVAILABLE);
        }
        return stripTrailingSlash(baseUrl) + "/" + stripLeadingSlash(objectKey);
    }

    private HeadObjectResponse headObject(String objectKey) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new CommonException(ErrorCode.STORAGE_UPLOAD_NOT_FOUND);
            }
            throw storageUnavailable(e);
        } catch (SdkClientException e) {
            throw storageUnavailable(e);
        }
    }

    private String requiredETag(String objectKey, HeadObjectResponse headObject) {
        String eTag = headObject.eTag();
        if (eTag == null || eTag.isBlank()) {
            log.warn("S3 HeadObject 응답에 ETag가 없습니다. objectKey={}", objectKey);
            throw new CommonException(ErrorCode.STORAGE_UNAVAILABLE);
        }
        return eTag;
    }

    private void deleteIgnoringFailure(String objectKey, String eTag) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .ifMatch(eTag)
                    .build());
        } catch (S3Exception | SdkClientException e) {
            log.warn("임시 업로드 객체 삭제에 실패했습니다. objectKey={}, errorType={}",
                    objectKey, e.getClass().getSimpleName());
        }
    }

    private CommonException storageUnavailable(Exception cause) {
        log.warn("S3 요청에 실패했습니다. errorType={}", cause.getClass().getSimpleName());
        return new CommonException(ErrorCode.STORAGE_UNAVAILABLE);
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String stripLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
