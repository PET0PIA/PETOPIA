package com.ms.petopia.global.storage;

import com.ms.petopia.global.storage.dto.PresignedUpload;

import java.util.Optional;

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

    /**
     * 공개 URL을 객체 키로 되돌린다({@link #toPublicUrl}의 역연산).
     *
     * <p>지금 설정된 공개 base URL로 만든 주소가 아니거나 확정 객체 접두사 밖을 가리키면
     * 빈 값을 준다. 고아 객체 정리처럼 "DB에 남은 URL로 지울 객체를 찾는" 작업이 엉뚱한
     * 객체를 지우지 않게 막는 안전장치다 - 키를 특정할 수 없으면 지우지 않는 편이 맞다.
     */
    Optional<String> toObjectKey(String publicUrl);
}
