# S3 Presigned URL 업로드 — 공통 기반 실행계획

> 작성일: 2026-08-04  
> 브랜치: `feature/s3-file`  
> 목표: 파일 바이트는 S3로 직접 전송하고, 백엔드는 업로드 URL 발급과 업로드 결과 검증을 위한 공통 모듈만 제공한다.

## 1. 범위

### 이번 작업에 포함

- AWS SDK와 `S3Client`/`S3Presigner` 공통 설정
- 업로드 정책(파일 형식·Content-Type·최대 용량)과 object key 생성기
- Presigned PUT URL 발급 API
- tmp 객체를 검사해 최종 object key를 만드는 `StorageService` 공통 로직
- 인증, 예외 변환, 민감한 presigned URL 로그 마스킹
- 단위 테스트와 웹 계층 테스트

### 이번 작업에서 명시적으로 제외

- **모든 도메인 DTO, 서비스, 컨트롤러, mapper, DB 컬럼 변경**
- 도메인 요청에서 object key를 받아 `confirm`하는 연결 작업
- 업로드한 파일을 특정 사용자/도메인 리소스에 귀속하는 메타 테이블
- 프론트엔드 화면/업로드 유틸
- S3 버킷, IAM, CORS, CloudFront, 라이프사이클, 배포/CI 설정

따라서 이번 브랜치는 “업로드 가능한 공통 기반”까지만 만든다. 어떤 도메인이 어떤 파일 정책을 쓸지와, 확정된 키를 어느 DB 컬럼에 저장할지는 후속 작업에서 결정한다.

## 2. 완료 기준

1. 인증된 사용자는 이미지 업로드용 presigned PUT URL을 발급받을 수 있다.
2. 허용되지 않은 확장자, Content-Type 불일치, 요청 용량 초과는 S3 호출 전 400/413으로 거절된다.
3. `StorageService#confirm`은 tmp key 형식과 `HeadObject`의 실제 용량/타입을 검증하고, 정상 객체만 최종 key로 복사한다.
4. S3 SDK 예외가 외부에 노출되지 않고 서비스 오류 코드로 변환된다.
5. `uploadUrl`과 서명 쿼리 문자열이 HTTP 로그에 남지 않는다.
6. 실제 AWS 계정 없이 모든 테스트가 통과한다.

## 3. 고정할 공통 계약

### 3-1. 정책 단위

도메인 이름을 enum에 넣지 않는다. 이번 단계의 정책은 재사용 가능한 파일 특성으로만 정의한다.

```java
public enum UploadPolicy {
    IMAGE(
        "image",
        Set.of("jpg", "jpeg", "png", "webp"),
        Set.of("image/jpeg", "image/png", "image/webp"),
        10 * 1024 * 1024
    )
}
```

- 이후 도메인이 필요하면 자체 코드에서 `UploadPolicy.IMAGE`를 선택한다.
- PDF, 동영상, 비공개 문서는 이번 정책과 API에 포함하지 않는다.
- 확장자와 Content-Type은 1:1 매핑으로 검증한다. 예를 들어 `.jpg`는 `image/jpeg`만 허용한다.

### 3-2. 키 규칙

```text
임시 키: tmp/image/{uuid}.{ext}
최종 키: uploads/image/{yyyy}/{MM}/{dd}/{uuid}.{ext}
```

- 원본 파일명은 저장하지 않는다. 표시가 필요해질 때만 도메인 모델에 별도 필드를 추가한다.
- 키 생성에 사용하는 확장자는 파일명의 마지막 확장자를 `Locale.ROOT`로 소문자화한 값이다.
- `confirm`은 임시 키에만 허용되며, `tmp/image/` 밖의 키, `..`, 절대 경로, UUID 형식 불일치를 모두 거절한다.

### 3-3. HTTP API

`POST /api/files/presigned-upload`

요청:

```json
{
  "policy": "IMAGE",
  "filename": "profile.png",
  "contentType": "image/png",
  "size": 3145728
}
```

응답:

```json
{
  "uploadUrl": "https://...",
  "objectKey": "tmp/image/550e8400-e29b-41d4-a716-446655440000.png",
  "expiresInSeconds": 300
}
```

- `size`는 조기 실패용이며 보안 검증은 아니다. 실제 검증은 `confirm`의 `HeadObject` 결과로 한다.
- 클라이언트는 presign 요청에 사용한 것과 같은 `Content-Type`으로 PUT해야 한다.
- 확정 HTTP API는 만들지 않는다. 공용 confirm API를 열면 소유권이 확인되지 않은 tmp 파일을 다른 리소스에 연결할 여지가 생긴다. 향후 도메인 서비스만 소유권을 검증한 뒤 `StorageService#confirm`을 호출한다. 이 호출은 S3 내부 연산일 뿐 DB 트랜잭션과 원자적이지 않다.

## 4. 구현 단계

### A. 의존성 및 설정 바인딩

수정/생성 대상:

```text
build.gradle
src/main/java/com/ms/petopia/PetopiaApplication.java
src/main/java/com/ms/petopia/global/storage/StorageProperties.java
src/main/java/com/ms/petopia/global/storage/S3Config.java
src/main/resources/application*.yaml
```

작업:

1. AWS SDK v2 BOM과 `software.amazon.awssdk:s3`를 추가한다. `S3Presigner`는 `s3` 모듈에 포함되어 있어 별도 presigner 의존성은 추가하지 않는다.
2. `StorageProperties`를 `@ConfigurationProperties("petopia.storage")` record로 만든다.
3. 속성은 `bucket`, `region`, `publicBaseUrl`, `presignExpiry`, `tmpPrefix`, `confirmedPrefix`만 둔다.
4. `@ConfigurationPropertiesScan`을 활성화한다.
5. `S3Client`와 `S3Presigner` bean은 `Region.of(region)`과 `DefaultCredentialsProvider.create()`로 생성한다.
6. 설정 파일에는 개발용 placeholder/default만 두고, 실제 값 주입과 배포 파일 변경은 이번 작업에서 하지 않는다.

완료 조건:

- `./gradlew compileJava` 통과
- 자격증명이 없는 테스트 환경에서도 application context 생성 자체는 실패하지 않음

### B. 파일 정책과 키 생성기

생성 대상:

```text
src/main/java/com/ms/petopia/global/storage/UploadPolicy.java
src/main/java/com/ms/petopia/global/storage/ObjectKeyGenerator.java
```

작업:

1. `UploadPolicy`가 `allowedExtensions`, `contentTypeFor(extension)`, `maxBytes`, `validate(...)`를 제공하게 한다.
2. filename 길이는 API DTO에서 최대 255자로 제한하고, generator는 마지막 확장자만 사용한다.
3. UUID·날짜 기반으로 tmp/final key를 각각 생성한다.
4. `isTemporaryKeyFor(policy, key)`에서 prefix, 세그먼트 수, UUID, 확장자를 검증한다.

완료 조건:

- `.JPG` → `jpg` 정상화
- 빈 확장자, `.jpg + image/png`, `../x.png`, `tmp/image/not-uuid.png`가 모두 거절됨
- 10MB 경계값(정확히 10MB 허용, 초과 거절) 검증

### C. 예외 코드와 S3 어댑터

수정/생성 대상:

```text
src/main/java/com/ms/petopia/global/exception/ErrorCode.java
src/main/java/com/ms/petopia/global/storage/StorageService.java
src/main/java/com/ms/petopia/global/storage/S3StorageService.java
src/main/java/com/ms/petopia/global/storage/dto/PresignedUpload.java
```

추가할 오류 코드:

| 코드 | HTTP | 발생 조건 |
|---|---:|---|
| `STORAGE_UNSUPPORTED_EXTENSION` | 400 | 정책 밖 확장자 |
| `STORAGE_CONTENT_TYPE_MISMATCH` | 400 | 확장자/정책과 타입 불일치 |
| `STORAGE_FILE_TOO_LARGE` | 413 | 요청 또는 실제 객체 용량 초과 |
| `STORAGE_INVALID_OBJECT_KEY` | 400 | 형식이 맞지 않는 tmp key |
| `STORAGE_UPLOAD_NOT_FOUND` | 400 | 업로드되지 않았거나 tmp 객체가 없음 |
| `STORAGE_UNAVAILABLE` | 503 | S3/SDK 통신 실패 |
| `STORAGE_UPLOAD_CHANGED` | 409 | `HeadObject` 검증 뒤 tmp 객체가 변경되어 조건부 복사에 실패 |

`StorageService` 공개 메서드:

```java
PresignedUpload presignPut(UploadPolicy policy, String filename, String contentType, long size);
String confirm(String temporaryObjectKey, UploadPolicy policy);
void delete(String objectKey);
String toPublicUrl(String objectKey);
```

`confirm` 순서:

1. key 형식을 로컬에서 검증한다. 실패 시 S3를 호출하지 않는다.
2. `HeadObject`로 실제 `contentLength`, `contentType`을 읽는다.
3. 정책 재검증에 실패하면 해당 tmp 객체 삭제를 시도한 뒤 오류를 반환한다.
4. `HeadObject`가 반환한 ETag를 `copySourceIfMatch`에 넣어 날짜 기반 최종 키로 조건부 `CopyObject`한다. ETag가 불일치하면 409으로 확정을 실패시키고 tmp 객체를 삭제하지 않는다.
5. Copy 성공 후에도 같은 ETag를 `DeleteObject.ifMatch`에 넣어 tmp 객체를 삭제한다. 복사 직후 새 객체가 업로드됐으면 삭제하지 않는다.
6. tmp 삭제가 실패해도 복사 성공을 되돌리지 않는다. `warn` 로그만 남긴다.

`confirm`은 S3 객체만 다루며 DB 상태를 생성·변경하지 않는다. 따라서 호출 도메인은 S3 확정 성공 뒤 DB 트랜잭션이 롤백될 수 있음을 전제로, 보상 삭제 또는 재시도 가능한 상태 전이 흐름을 별도로 구현해야 한다.

예외 변환:

- 객체 없음(404/`NoSuchKeyException`) → `STORAGE_UPLOAD_NOT_FOUND`
- `S3Exception`, `SdkClientException` → `STORAGE_UNAVAILABLE`
- 로그에는 object key와 오류 종류만 남기고, URL·쿼리·자격증명은 남기지 않는다.

완료 조건:

- 정상 흐름에서 `HeadObject → 조건부 CopyObject → 조건부 DeleteObject` 호출 순서와 ETag 전달 검증
- 타입/용량 실패 시 `DeleteObject` 호출 검증
- Head 이후 Copy가 412/409으로 실패하면 409을 반환하고 DeleteObject를 호출하지 않음 검증
- tmp 삭제 실패 시 최종 key 반환과 warn 로그 검증

### D. URL 발급 API와 보안/로그 방어

수정/생성 대상:

```text
src/main/java/com/ms/petopia/api/file/controller/FileController.java
src/main/java/com/ms/petopia/api/file/dto/PresignedUploadRequest.java
src/main/java/com/ms/petopia/api/file/dto/PresignedUploadResponse.java
src/main/java/com/ms/petopia/global/security/SecurityConfig.java
src/main/java/com/ms/petopia/global/logging/HttpLoggingFilter.java
```

작업:

1. 요청 DTO: `@NotNull UploadPolicy policy`, `@NotBlank @Size(max=255) filename`, `@NotBlank contentType`, `@Positive size`.
2. 파일 controller는 `StorageService#presignPut`만 호출하고 응답 DTO로 변환한다.
3. `SecurityConfig`에 `POST /api/files/presigned-upload`의 `.authenticated()` 규칙을 추가한다. 기존 도메인 API의 인가 정책은 건드리지 않는다.
4. 이 단계에서는 특정 role을 제한하지 않는다. 어떤 role이 어떤 정책을 쓸지는 도메인 연결 시점에 결정한다.
5. `HttpLoggingFilter`의 JSON 민감 필드 패턴에 `uploadUrl`을 추가한다.
6. 반환 예외 메시지에도 presigned URL이 삽입되지 않는지 확인한다.

완료 조건:

- 미인증 401, 유효 요청 200, validation 실패 400
- HTTP 로그에서 `"uploadUrl":"****"` 확인
- 존재하지 않는 enum 값이 400으로 처리됨

## 5. 테스트 계획

| 테스트 | 검증 범위 |
|---|---|
| `UploadPolicyTest` | 확장자·Content-Type 1:1 매핑, 용량 경계 |
| `ObjectKeyGeneratorTest` | tmp/final 키 형식, 대문자 확장자, 경로 조작, UUID 검사 |
| `S3StorageServiceTest` | presign, 없는 객체, 실제 용량/타입 불일치, Copy/Delete 순서, SDK 예외 변환 |
| `FileControllerTest` | 401/400/200, DTO 검증, StorageService 호출 인자 |
| `HttpLoggingFilterTest` 보강 | 응답 JSON의 `uploadUrl` 마스킹 |

- `S3Client`와 `S3Presigner`는 모두 mock한다. 실제 AWS 호출 테스트는 만들지 않는다.
- UUID와 현재 시각은 주입하거나 형식 matcher로 검증해 테스트가 환경·시간에 의존하지 않게 한다.
- `./gradlew test`가 최종 검증 명령이다.

## 6. 커밋과 리뷰 순서

| 커밋 | 내용 | 리뷰 확인점 |
|---|---|---|
| 1 | AWS SDK, properties, S3 bean | 설정 바인딩과 자격증명 미포함 |
| 2 | `UploadPolicy`, key generator, 오류 코드, 테스트 | 모든 입력 검증이 중앙화됨 |
| 3 | `S3StorageService`, mock 단위 테스트 | `confirm`의 Head/Copy/Delete 및 예외 변환 |
| 4 | 파일 발급 controller, 보안, 로그 마스킹, 웹 테스트 | URL 발급 인증과 서명 URL 비노출 |

## 7. 머지 전 체크리스트

- [ ] 어떤 도메인 코드도 수정하지 않았음
- [ ] DB 마이그레이션과 mapper 변경이 없음
- [ ] tmp key 외의 객체를 `confirm`할 수 없음
- [ ] 발급 시와 확정 시 동일한 `UploadPolicy` 검증을 사용함
- [ ] 실제 `HeadObject` 메타데이터로 용량·Content-Type을 재검증함
- [ ] presigned URL/서명 쿼리가 로그나 예외 메시지에 없음
- [ ] 발급 endpoint가 인증 없이 호출되지 않음
- [ ] 모든 테스트가 실제 AWS 자격증명 없이 통과함

## 8. 후속 작업 (이번 브랜치 제외)

1. 파일 메타 테이블을 도입해 업로더·정책·도메인 리소스·tmp/final key·`PENDING`/`CONFIRMED` 상태를 저장하고, tmp key 소유권을 검증한다.
2. 첫 도메인에서 PENDING 레코드를 만든 뒤, 재시도 가능한 확정 작업으로 `confirm`과 DB의 CONFIRMED 전이를 조율한다. 최종 key는 재시도에도 같은 값을 쓰도록 DB에 먼저 기록한다.
3. S3 확정 성공 뒤 DB 갱신·커밋이 실패하면 보상 삭제를 시도하고, 실패한 정리는 별도 재시도 작업으로 남긴다. DB 트랜잭션과 함께 롤백되는 outbox만으로는 이 보상을 보장할 수 없다.
4. 교체/삭제 시 이전 객체 삭제를 도메인 규칙에 맞게 추가한다.
5. 비공개 파일 정책과 presigned GET은 별도 설계/구현한다.
