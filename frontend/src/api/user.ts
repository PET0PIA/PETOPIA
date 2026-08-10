import { apiClient } from "./client";

/** GET/PATCH /api/users/me. 백엔드 UserMeResponse에 맞춘다. */
export interface UserMe {
  userId: number;
  email: string;
  nickname: string;
  /** YYYY-MM-DD */
  birthDate: string;
  phone: string;
  gender: "남성" | "여성";
  address: string;
  role: "USER" | "VENDOR" | "EVENT_ADMIN" | "SUPER_ADMIN";
  status: string;
  emailVerified: boolean;
  createdAt: string;
}

/**
 * PATCH /api/users/me 요청. 보낸 필드만 반영되고(undefined는 JSON.stringify에서 아예 빠짐),
 * 안 보낸 필드는 서버가 기존 값 그대로 둔다. email/role/status는 이 화면에서 못 바꾼다.
 */
export interface UpdateUserRequest {
  nickname?: string;
  /** YYYY-MM-DD */
  birthDate?: string;
  phone?: string;
  gender?: "남성" | "여성";
  address?: string;
}

export function getMe() {
  return apiClient.get<UserMe>("/api/users/me");
}

export function updateMe(payload: UpdateUserRequest) {
  return apiClient.patch<UserMe>("/api/users/me", payload);
}
