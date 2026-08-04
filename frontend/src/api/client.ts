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

async function request<TResponse>(path: string, init?: RequestInit): Promise<TResponse> {
  const response = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });

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
