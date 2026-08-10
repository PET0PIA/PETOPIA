/**
 * fetch 기반 공통 API 클라이언트.
 *
 * vite.config.ts의 `/api` 프록시가 백엔드(:8080)로 요청을 넘겨준다.
 * 에러 응답은 GlobalExceptionHandler의 ErrorResponse 형태(code/message/status)를 그대로 파싱한다.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

interface ErrorResponseBody {
  code?: string;
  message?: string;
  status?: number;
}

/**
 * Access Token은 메모리에만 들고 있는다 (localStorage 저장 안 함).
 * XSS로 스크립트가 뚫려도 새로고침하면 사라지도록 하기 위함 — Refresh Token을 httpOnly
 * 쿠키로 분리한 것과 같은 방향. 대신 새로고침 시 사라진 토큰은 AuthProvider가 마운트될 때
 * refreshAccessToken()으로 다시 복구한다.
 */
let accessToken: string | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

/**
 * 401 응답을 받았을 때 토큰 재발급을 시도하는 함수. auth.ts가 client.ts를 이미 쓰고 있어서
 * client.ts가 auth.ts를 직접 import하면 순환 참조가 생긴다 — 그래서 auth.ts 쪽에서
 * 자기 refreshAccessToken()을 여기에 등록해주는 방식으로 역참조를 끊는다.
 */
type RefreshHandler = () => Promise<string>;
let refreshHandler: RefreshHandler | null = null;

export function setRefreshHandler(handler: RefreshHandler | null) {
  refreshHandler = handler;
}

/** 여러 요청이 동시에 401을 받아도 재발급 요청은 한 번만 나가도록 Promise를 공유한다. */
let refreshPromise: Promise<string> | null = null;

function refreshAccessTokenOnce(): Promise<string> {
  if (!refreshHandler) return Promise.reject(new Error("refresh handler가 등록되지 않았어요."));
  if (!refreshPromise) {
    refreshPromise = refreshHandler().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function request<TResponse>(path: string, init?: RequestInit, isRetry = false): Promise<TResponse> {
  const headers: Record<string, string> = { "Content-Type": "application/json", ...(init?.headers as Record<string, string>) };
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  // refreshToken은 httpOnly 쿠키라 JS가 직접 못 읽지만, credentials: "include"로 보내야
  // 브라우저가 쿠키를 요청에 실어준다.
  const response = await fetch(path, { ...init, headers, credentials: "include" });

  // Access Token 만료로 401이 났으면 재발급 후 원래 요청을 한 번만 재시도한다.
  // /api/auth로 시작하는 인증 자체 엔드포인트(로그인 실패 등)는 재시도 대상에서 제외.
  const isAuthEndpoint = path.startsWith("/api/auth/");
  if (response.status === 401 && !isRetry && !isAuthEndpoint && refreshHandler) {
    try {
      const newToken = await refreshAccessTokenOnce();
      setAccessToken(newToken);
      return request<TResponse>(path, init, true);
    } catch {
      setAccessToken(null);
      // 재발급도 실패하면 원래 401 응답을 그대로 아래에서 처리하도록 흘려보낸다.
    }
  }

  if (response.status === 204) {
    return undefined as TResponse;
  }

  const text = await response.text();
  const body = text.length > 0 ? JSON.parse(text) : undefined;

  if (!response.ok) {
    const errorBody = body as ErrorResponseBody | undefined;
    throw new ApiError(errorBody?.message ?? "요청을 처리하지 못했어요.", response.status, errorBody?.code);
  }

  return body as TResponse;
}

/**
 * 참가업체 도메인(business/application/recruitNotice/booth)의 성공 응답 포맷.
 * fair/payment 등 다른 도메인은 DTO를 그대로 반환해서 apiClient 레벨에서 일괄
 * 벗기지 않고, 이 포맷을 쓰는 도메인의 api 모듈에서만 개별적으로 unwrap한다.
 */
export interface ApiEnvelope<T> {
  success: boolean;
  status: number;
  code: string;
  message: string | null;
  data: T;
}

export const apiClient = {
  get: <T>(path: string, init?: RequestInit) => request<T>(path, { ...init, method: "GET" }),
  post: <T>(path: string, payload?: unknown, init?: RequestInit) =>
    request<T>(path, { ...init, method: "POST", body: payload === undefined ? undefined : JSON.stringify(payload) }),
  put: <T>(path: string, payload?: unknown, init?: RequestInit) =>
    request<T>(path, { ...init, method: "PUT", body: payload === undefined ? undefined : JSON.stringify(payload) }),
  patch: <T>(path: string, payload?: unknown, init?: RequestInit) =>
    request<T>(path, { ...init, method: "PATCH", body: payload === undefined ? undefined : JSON.stringify(payload) }),
  delete: <T>(path: string, init?: RequestInit) => request<T>(path, { ...init, method: "DELETE" }),
};
