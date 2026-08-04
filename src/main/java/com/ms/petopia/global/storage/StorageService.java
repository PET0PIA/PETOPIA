package com.ms.petopia.global.storage;

import com.ms.petopia.global.storage.dto.PresignedUpload;

public interface StorageService {

    PresignedUpload presignPut(UploadPolicy policy, String filename, String contentType, long size);

    /**
     * 검증된 임시 객체를 S3 안에서 최종 객체로 승격하는 내부 연산이다.
     * DB 트랜잭션에 참여하거나 DB 상태 변경과 원자성을 보장하지 않으므로,
     * 호출 도메인은 소유권 검증과 DB 실패 시 보상·재시도 처리를 책임진다.
     */
    String confirm(String temporaryObjectKey, UploadPolicy policy);

    void delete(String objectKey);

    String toPublicUrl(String objectKey);
}
