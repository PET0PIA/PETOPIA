import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { setAccessToken } from "../api/client";
import {
  completeOAuthSignup as completeOAuthSignupRequest,
  decodeAccessToken,
  exchangeOAuthLogin,
  login as loginRequest,
  logout as logoutRequest,
  refreshAccessToken,
  type AccessTokenPayload,
  type EmailLoginRequest,
  type OAuthSignupCompleteRequest,
} from "../api/auth";

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

interface AuthContextValue {
  user: AccessTokenPayload | null;
  status: AuthStatus;
  login: (payload: EmailLoginRequest) => Promise<void>;
  loginWithOAuthCode: (code: string) => Promise<void>;
  completeOAuthSignup: (payload: OAuthSignupCompleteRequest) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AccessTokenPayload | null>(null);
  const [status, setStatus] = useState<AuthStatus>("loading");

  useEffect(() => {
    // 새로고침하면 메모리에 들고 있던 accessToken은 사라진다. httpOnly로 남아있는
    // refreshToken 쿠키로 재발급을 한 번 시도해서 로그인 상태를 복구한다(silent refresh).
    // 쿠키가 없거나 만료됐으면 그냥 비로그인 상태로 확정한다.
    let cancelled = false;
    (async () => {
      try {
        const accessToken = await refreshAccessToken();
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

  return (
    <AuthContext.Provider value={{ user, status, login, loginWithOAuthCode, completeOAuthSignup, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth()는 AuthProvider 내부에서만 사용할 수 있어요.");
  return context;
}
