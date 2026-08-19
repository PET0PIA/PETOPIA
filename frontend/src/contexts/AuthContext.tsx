import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { refreshAccessTokenOnce, setAccessToken } from "../api/client";
import {
  adminLogin as adminLoginRequest,
  completeOAuthSignup as completeOAuthSignupRequest,
  decodeAccessToken,
  exchangeOAuthLogin,
  login as loginRequest,
  logout as logoutRequest,
  type AccessTokenPayload,
  type EmailLoginRequest,
  type OAuthSignupCompleteRequest,
} from "../api/auth";

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

interface AuthContextValue {
  user: AccessTokenPayload | null;
  status: AuthStatus;
  login: (payload: EmailLoginRequest) => Promise<void>;
  loginAsAdmin: (payload: EmailLoginRequest) => Promise<void>;
  loginWithOAuthCode: (code: string) => Promise<void>;
  completeOAuthSignup: (payload: OAuthSignupCompleteRequest) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AccessTokenPayload | null>(null);
  const [status, setStatus] = useState<AuthStatus>("loading");

  useEffect(() => {
    // 새로고침하면 메모리에 들고 있던 accessToken은 사라진다. httpOnly로 남아있는
    // refreshToken 쿠키로 재발급을 한 번 시도해서 로그인 상태를 복구한다(silent refresh).
    // 쿠키가 없거나 만료됐으면 그냥 비로그인 상태로 확정한다.
    // client.ts의 refreshAccessTokenOnce()를 같이 써서, 이 시점에 다른 컴포넌트의 API
    // 요청이 401을 맞고 자기 나름대로 재발급을 시도하더라도 실제 네트워크 요청은 하나로
    // 합쳐지게 한다(안 그러면 Rotation 방식인 Refresh Token을 두 번 동시에 쓰려다 하나가 실패한다).
    let cancelled = false;
    (async () => {
      try {
        const accessToken = await refreshAccessTokenOnce();
        if (cancelled) return;
        setAccessToken(accessToken);
        setUser(decodeAccessToken(accessToken));
        setStatus("authenticated");
      } catch {
        if (cancelled) return;
        setStatus("unauthenticated");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function login(payload: EmailLoginRequest) {
    const decoded = await loginRequest(payload);
    setUser(decoded);
    setStatus("authenticated");
  }

  async function loginAsAdmin(payload: EmailLoginRequest) {
    const decoded = await adminLoginRequest(payload);
    setUser(decoded);
    setStatus("authenticated");
  }

  async function loginWithOAuthCode(code: string) {
    const decoded = await exchangeOAuthLogin(code);
    setUser(decoded);
    setStatus("authenticated");
  }

  async function completeOAuthSignup(payload: OAuthSignupCompleteRequest) {
    const decoded = await completeOAuthSignupRequest(payload);
    setUser(decoded);
    setStatus("authenticated");
  }

  async function logout() {
    try {
      await logoutRequest();
    } finally {
      setUser(null);
      setStatus("unauthenticated");
    }
  }

  async function refreshUser() {
  try {
    // role은 JWT payload에 박혀있어서, 서버에서 사업자 취소 등으로 role이
    // 바뀌어도 토큰을 다시 발급받기 전엔 프론트가 알 방법이 없다.
    const token = await refreshAccessTokenOnce();
    setUser(decodeAccessToken(token));
  } catch {
    // 리프레시 실패(토큰 만료 등)는 무시 — 기존 401 처리 흐름이 로그아웃을 담당
  }
}

  return (
    <AuthContext.Provider value={{ user, status, login, loginAsAdmin, loginWithOAuthCode, completeOAuthSignup, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth()는 AuthProvider 내부에서만 사용할 수 있어요.");
  return context;
}
