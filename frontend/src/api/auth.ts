import { apiClient, setAccessToken, setRefreshHandler } from "./client";

export interface EmailLoginRequest {
  email: string;
  password: string;
}

interface LoginResponse {
  accessToken: string;
}

export type UserRole = "USER" | "VENDOR" | "EVENT_ADMIN" | "SUPER_ADMIN";

export interface AccessTokenPayload {
  userId: number;
  role: UserRole;
  /** 초 단위 UNIX timestamp (JWT 표준) */
  exp: number;
}

/**
 * Access Token의 payload(가운데 조각)만 디코딩한다. 서명 검증은 하지 않는다 — 그건 서버 몫이고,
 * 여기서는 화면에서 "로그인 상태/역할에 따라 뭘 보여줄지"만 판단하려는 용도다.
 */
export function decodeAccessToken(token: string): AccessTokenPayload {
  const payloadSegment = token.split(".")[1];
  const base64 = payloadSegment.replace(/-/g, "+").replace(/_/g, "/");
  const bytes = Uint8Array.from(atob(base64), (char) => char.charCodeAt(0));
  const payload = JSON.parse(new TextDecoder().decode(bytes)) as { sub: string; role: UserRole; exp: number };
  return { userId: Number(payload.sub), role: payload.role, exp: payload.exp };
}

/** 로그인 성공 시 accessToken을 client.ts에 저장하고, 디코딩한 payload를 돌려준다. */
export async function login(payload: EmailLoginRequest): Promise<AccessTokenPayload> {
  const { accessToken } = await apiClient.post<LoginResponse>("/api/auth/email/login", payload);
  setAccessToken(accessToken);
  return decodeAccessToken(accessToken);
}

/** SUPER_ADMIN 전용 로그인 - EVENT_ADMIN/일반 유저는 이 엔드포인트로 로그인 불가(서버가 role 체크 후 거부). */
export async function adminLogin(payload: EmailLoginRequest): Promise<AccessTokenPayload> {
  const { accessToken } = await apiClient.post<LoginResponse>("/api/admin/auth/login", payload);
  setAccessToken(accessToken);
  return decodeAccessToken(accessToken);
}

/**
 * httpOnly refreshToken 쿠키로 Access Token을 재발급받는다.
 * 페이지 새로고침 직후(메모리의 accessToken이 비어있을 때) silent refresh 용도로도 쓰인다.
 */
export async function refreshAccessToken(): Promise<string> {
  const { accessToken } = await apiClient.post<LoginResponse>("/api/auth/refresh");
  return accessToken;
}

export async function logout(): Promise<void> {
  try {
    await apiClient.post<void>("/api/auth/logout");
  } finally {
    setAccessToken(null);
  }
}

// client.ts가 401을 받았을 때 재발급을 시도할 수 있도록, 순환 import 없이 함수를 등록해준다.
setRefreshHandler(refreshAccessToken);

// --- 회원가입 ---

export interface EmailSignupRequest {
  email: string;
  password: string;
  passwordConfirm: string;
  nickname: string;
  /** YYYY-MM-DD */
  birthDate: string;
  phone: string;
  gender: "남성" | "여성";
  address: string;
  agreedTerms: boolean;
  agreedPrivacy: boolean;
}

export interface EmailCheckResponse {
  available: boolean;
}

/** 이메일 중복 확인. 인증까지 끝난 계정이 있으면 false. */
export function checkEmailAvailable(email: string): Promise<EmailCheckResponse> {
  return apiClient.get<EmailCheckResponse>(`/api/auth/email/check?email=${encodeURIComponent(email)}`);
}

/**
 * 회원가입. 성공해도 바로 로그인 가능한 상태는 아니다 - 서버가 status: PENDING으로
 * 만들고 인증 메일을 보낸다. verifyEmail()까지 끝나야 로그인이 열린다.
 */
export function signup(payload: EmailSignupRequest): Promise<void> {
  return apiClient.post<void>("/api/auth/email/signup", payload);
}

export function verifyEmail(email: string, token: string): Promise<void> {
  return apiClient.post<void>("/api/auth/email/verify", { email, token });
}

export function resendVerification(email: string): Promise<void> {
  return apiClient.post<void>("/api/auth/email/verify/resend", { email });
}

// --- 비밀번호 찾기 ---

/** 계정 존재 여부와 무관하게 항상 성공 응답 - 이메일 존재 여부가 노출되지 않도록 서버가 그렇게 처리함. */
export function requestPasswordReset(email: string): Promise<void> {
  return apiClient.post<void>("/api/auth/password/reset-request", { email });
}

export function resetPassword(token: string, newPassword: string): Promise<void> {
  return apiClient.post<void>("/api/auth/password/reset", { token, newPassword });
}

// --- 소셜 로그인 ---

export type OAuthProvider = "google" | "naver";

/**
 * 소셜 로그인 시작 주소. fetch가 아니라 `window.location.href = oauthLoginUrl(...)`로
 * 브라우저 자체를 이동시켜야 한다 - 서버가 302로 구글/네이버 동의화면까지 보내주는 흐름이라
 * XHR로는 못 따라감(리다이렉트 체인 끝에 있는 Set-Cookie도 받아야 함).
 */
export function oauthLoginUrl(provider: OAuthProvider): string {
  return `/api/auth/oauth/${provider}/login`;
}

/** 기존 유저 - /oauth/callback?type=login&code=... 의 code로 토큰 교환 */
export async function exchangeOAuthLogin(code: string): Promise<AccessTokenPayload> {
  const { accessToken } = await apiClient.post<LoginResponse>("/api/auth/oauth/exchange", { code });
  setAccessToken(accessToken);
  return decodeAccessToken(accessToken);
}

export interface OAuthSignupCompleteRequest {
  /** /oauth/callback?type=signup&code=... 의 code. 서버가 여기 email/provider/oauthId를 물려서 들고 있음 */
  tempKey: string;
  nickname: string;
  /** YYYY-MM-DD */
  birthDate: string;
  phone: string;
  gender: "남성" | "여성";
  address: string;
  agreedTerms: boolean;
  agreedPrivacy: boolean;
}

/** 신규 유저 - 소셜 인증 후 필수 프로필/약관을 마저 받아 가입을 마무리 */
export async function completeOAuthSignup(payload: OAuthSignupCompleteRequest): Promise<AccessTokenPayload> {
  const { accessToken } = await apiClient.post<LoginResponse>("/api/auth/signup/complete", payload);
  setAccessToken(accessToken);
  return decodeAccessToken(accessToken);
}
