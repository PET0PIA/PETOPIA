import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";
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
  // login/logout이 일어날 때마다 올려서, 그 이전에 시작된 비동기 refreshUser 응답이
  // 뒤늦게 돌아와도 user를 덮어쓰지 못하게 막는다.
  const authGeneration = useRef(0);

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
    authGeneration.current += 1;
    const decoded = await loginRequest(payload);
    setUser(decoded);
    setStatus("authenticated");
  }

  async function loginAsAdmin(payload: EmailLoginRequest) {
    authGeneration.current += 1;
    const decoded = await adminLoginRequest(payload);
    setUser(decoded);
    setStatus("authenticated");
  }

  async function loginWithOAuthCode(code: string) {
    authGeneration.current += 1;
    const decoded = await exchangeOAuthLogin(code);
    setUser(decoded);
    setStatus("authenticated");
  }

  async function completeOAuthSignup(payload: OAuthSignupCompleteRequest) {
    authGeneration.current += 1;
    const decoded = await completeOAuthSignupRequest(payload);
    setUser(decoded);
    setStatus("authenticated");
  }

  async function logout() {
    authGeneration.current += 1;
    try {
      await logoutRequest();
    } finally {
      setUser(null);
      setStatus("unauthenticated");
    }
  }

  async function refreshUser() {
    const generation = authGeneration.current;
    try {
      // role은 JWT payload에 박혀있어서, 마운트 시 디코드한 값을 그대로 들고 있는 한
      // 서버에서 role이 바뀌어도(사업자 취소/승인 등) 프론트는 알 방법이 없다.
      // 토큰을 다시 발급받아 새 payload로 role을 재확인한다.
      const token = await refreshAccessTokenOnce();
      // await 도중 로그인/로그아웃 등으로 인증 상태가 이미 바뀌었으면, 뒤늦게 도착한
      // 이 결과로 user를 덮어쓰지 않는다.
      if (authGeneration.current !== generation) return;
      setAccessToken(token);
      setUser(decodeAccessToken(token));
    } catch {
      // 여기서 실패해도 로그아웃 처리는 하지 않는다 - 일시적인 네트워크 오류일 수 있고,
      // 세션이 실제로 끊겼다면 다음 API 호출이 401을 맞아 기존 흐름이 로그아웃을 처리한다.
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
