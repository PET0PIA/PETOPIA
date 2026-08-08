import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import type { UserRole } from "../api/auth";

interface ProtectedRouteProps {
  /** 지정하면 해당 role을 가진 사용자만 통과. 생략하면 로그인 여부만 확인. */
  roles?: UserRole[];
}

/**
 * 하위 라우트 전체를 감싸는 가드. react-router의 "레이아웃 라우트" 패턴으로 쓴다:
 *   <Route element={<ProtectedRoute roles={["EVENT_ADMIN"]} />}>
 *     <Route path="fair-admin" element={<FairAdminLayout />}>...</Route>
 *   </Route>
 * 통과하면 <Outlet/>이 실제 하위 라우트를 그대로 렌더링한다.
 *
 * 어디까지나 UX 가드다 — "여기 뚫려 있으니 화면을 그려도 되는지"만 판단할 뿐, 실제 권한
 * 검증은 매 API 요청마다 백엔드가 Authorization 헤더로 다시 확인한다.
 */
export function ProtectedRoute({ roles }: ProtectedRouteProps) {
  const { user, status } = useAuth();
  const location = useLocation();

  // 앱 시작 시 silent refresh(AuthProvider)가 끝나기 전까지는 로그인 여부를 아직 모른다.
  // 여기서 곧바로 /login으로 튕기면 새로고침할 때마다 로그인 화면이 깜빡이므로 잠시 대기.
  if (status === "loading") return null;

  if (status === "unauthenticated" || !user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
