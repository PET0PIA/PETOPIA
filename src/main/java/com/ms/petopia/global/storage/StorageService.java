package com.ms.petopia.global.storage;

import com.ms.petopia.global.storage.dto.PresignedUpload;

public interface StorageService {

    PresignedUpload presignPut(UploadPolicy policy, String filename, String contentType, long size);

    /**
     * 도메인 서비스가 자신의 트랜잭션 안에서 호출할 내부 확정 연산이다.
     * 외부 HTTP API로 노출하지 않는다.
     */
    String confirm(String temporaryObjectKey, UploadPolicy policy);

    void delete(String objectKey);

    String toPublicUrl(String objectKey);
}
