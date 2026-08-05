import { apiClient } from "./client";

/**
 * S3 presigned URL 직접 업로드 공용 유틸.
 *
 * 흐름: (1) 우리 서버에 presigned PUT URL 발급 요청 → (2) 브라우저가 S3에 파일을 직접 PUT →
 * (3) 호출부가 돌려받은 objectKey를 도메인 API(예: 홀 등록/수정) 요청에 실어 보내면, 서버가
 * tmp → uploads로 확정 처리한다. 3단계를 건너뛰면 파일이 tmp에 고아로 남는다 - 반드시
 * objectKey를 실제 저장 요청까지 이어서 써야 한다.
 *
 * presigned URL은 발급 후 짧게(수 분) 만료되므로, uploadImage()는 발급과 업로드를 한 번에
 * 처리하고 미리 발급해서 오래 들고 있지 않는다.
 */

export type StorageUploadPolicy = "IMAGE";

interface PresignedUploadRequest {
  policy: StorageUploadPolicy;
  filename: string;
  contentType: string;
  size: number;
}

interface PresignedUploadResponse {
  uploadUrl: string;
  objectKey: string;
  expiresInSeconds: number;
}

const IMAGE_EXTENSIONS = ["jpg", "jpeg", "png", "webp"];
const IMAGE_MAX_BYTES = 10 * 1024 * 1024;

const EXTENSION_CONTENT_TYPES: Record<string, string> = {
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  png: "image/png",
  webp: "image/webp",
};

function extensionOf(filename: string): string {
  return filename.split(".").pop()?.toLowerCase() ?? "";
}

/**
 * 업로드 전에 프론트에서 먼저 걸러주는 검증(서버 UploadPolicy.IMAGE와 같은 기준). presigned
 * 발급 요청 자체를 줄여줄 뿐, 서버 쪽 검증을 대신하지 않는다 - 서버도 동일 기준으로 다시 검사한다.
 */
export function validateImageFile(file: File): string | null {
  if (!IMAGE_EXTENSIONS.includes(extensionOf(file.name))) {
    return "jpg, png, webp 형식의 이미지만 업로드할 수 있어요.";
  }
  if (file.size > IMAGE_MAX_BYTES) {
    return "이미지는 10MB 이하만 업로드할 수 있어요.";
  }
  return null;
}

/** 일부 브라우저/OS는 특정 확장자의 file.type을 빈 문자열로 준다 - 그럴 때 확장자로 유추한다. */
function resolveContentType(file: File): string {
  return file.type || EXTENSION_CONTENT_TYPES[extensionOf(file.name)] || "application/octet-stream";
}

function issuePresignedUpload(payload: PresignedUploadRequest) {
  return apiClient.post<PresignedUploadResponse>("/api/files/presigned-upload", payload);
}

function putToS3(uploadUrl: string, file: File, contentType: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", uploadUrl);
    // Content-Type은 1단계에서 발급받은 서명에 포함된 값과 정확히 일치해야 한다.
    xhr.setRequestHeader("Content-Type", contentType);
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve();
      else reject(new Error(`이미지 업로드에 실패했어요. (status ${xhr.status})`));
    };
    xhr.onerror = () => reject(new Error("이미지 업로드 중 네트워크 오류가 발생했어요."));
    // Authorization 헤더는 절대 넣지 않는다 - S3 presigned 서명 대상이 아니라서 403
    // SignatureDoesNotMatch가 난다. FormData가 아니라 파일 자체를 바디로 그대로 보낸다.
    xhr.send(file);
  });
}

/** presigned URL 발급 → S3 PUT까지 처리하고, 도메인 API에 넘길 objectKey를 반환한다. */
export async function uploadImage(file: File): Promise<string> {
  const contentType = resolveContentType(file);
  const { uploadUrl, objectKey } = await issuePresignedUpload({
    policy: "IMAGE",
    filename: file.name,
    contentType,
    size: file.size,
  });
  await putToS3(uploadUrl, file, contentType);
  return objectKey;
}
