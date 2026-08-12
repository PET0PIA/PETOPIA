import { Bell, ChevronDown, LogOut, Menu, UserRound, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { publicNavigation, type NavigationItem } from "../../config/navigation";
import type { UserRole } from "../../api/auth";
import petopiaLogo from "../../assets/logo/petopiaLOGO.png";
import { getUnreadNotificationCount } from "../../api/notification";
import { getMe } from "../../api/user";
import { useAuth } from "../../contexts/AuthContext";
import { Button } from "../ui/Button";
import { DropdownMenu } from "../ui/DropdownMenu";

function useUnreadNotificationCount() {
  const [count, setCount] = useState(0);
  const { pathname } = useLocation();
  const { status } = useAuth();
  useEffect(() => {
    if (status !== "authenticated") return;
    let active = true;
    getUnreadNotificationCount()
      .then((value) => { if (active) setCount(value); })
      .catch(() => { if (active) setCount(0); /* 알림 배지는 조회 실패 시 0으로 유지한다. */ });
    return () => { active = false; };
  }, [pathname, status]);
  // 비로그인 상태면 0으로 취급한다(effect 안에서 동기 setState를 피하려고 여기서 처리).
  return status === "authenticated" ? count : 0;
}

// requiredRole이 지정된 메뉴는 그 role로 로그인했을 때만 보인다. 없으면 비로그인 포함 누구나.
function isVisibleForRole(item: NavigationItem, role: UserRole | null) {
  if (!item.requiredRole) return true;
  return role != null && item.requiredRole.includes(role);
}

function HeaderLogo() {
  return (
    <Link to="/" className="flex shrink-0 items-center gap-2.5" aria-label="PETOPIA 홈">
      <img src={petopiaLogo} alt="" className="h-10 w-auto object-contain" />
      <span className="text-2xl font-bold tracking-tight text-ink">PETOPIA</span>
    </Link>
  );
}

// 데스크톱 상단의 단일 링크 메뉴(드롭다운이 아닌 것). 현재 위치면 밑줄로 강조한다.
function TopNavLink({ to, label }: { to: string; label: string }) {
  const { pathname } = useLocation();
  const active = pathname === to || pathname.startsWith(`${to}/`);
  return (
    <Link
      to={to}
      aria-current={active ? "page" : undefined}
      className={`rounded-button px-3 py-2 text-sm font-semibold text-ink hover:bg-page ${active ? "underline underline-offset-8" : ""}`}
    >
      {label}
    </Link>
  );
}

// 로그인한 사용자의 프로필 메뉴. 역할에 따라 사업자 메뉴/관리자 콘솔 진입이 더해진다.
function ProfileMenu({ role, name, onLogout }: { role: UserRole; name: string; onLogout: () => void }) {
  const navigate = useNavigate();
  const items: { label: string; onSelect: () => void }[] = [
    { label: "마이페이지", onSelect: () => navigate("/mypage") },
    { label: "내 예약 목록", onSelect: () => navigate("/reservations/me") },
  ];
  if (role === "VENDOR") items.push({ label: "내 사업자 목록", onSelect: () => navigate("/businesses/me") });
  if (role === "EVENT_ADMIN") items.push({ label: "박람회 관리자 콘솔", onSelect: () => navigate("/fair-admin/fair") });
  if (role === "SUPER_ADMIN") items.push({ label: "최고 관리자 콘솔", onSelect: () => navigate("/admin") });
  items.push({ label: "로그아웃", onSelect: onLogout });
  return <DropdownMenu label={name} items={items} />;
}

function MobileSection({ item, onNavigate }: { item: NavigationItem; onNavigate: (path: string) => void }) {
  const [expanded, setExpanded] = useState(false);
  const children = item.children ?? [];
  return (
    <div className="border-b border-line py-1">
      <button
        type="button"
        className="flex w-full items-center justify-between px-1 py-3 text-left text-sm font-bold"
        aria-expanded={expanded}
        onClick={() => setExpanded((value) => !value)}
      >
        {item.label}
        <ChevronDown size={17} className={expanded ? "rotate-180 transition-transform" : "transition-transform"} />
      </button>
      {expanded && (
        <div className="pb-2 pl-3">
          {children.map((child) => (
            <button
              key={child.label}
              type="button"
              className="block w-full py-2 text-left text-sm text-muted hover:text-primary-strong"
              onClick={() => child.path && onNavigate(child.path)}
            >
              {child.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export function PublicHeader() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const navigate = useNavigate();
  const { user, status, logout } = useAuth();
  const unreadCount = useUnreadNotificationCount();
  const [nickname, setNickname] = useState<string | null>(null);

  // 로그인 상태면 프로필(닉네임)을 한 번 불러온다. JWT엔 이름이 없어 /users/me로 가져온다.
  // (비로그인일 때 nickname은 어차피 프로필 메뉴가 안 떠서 화면에 안 쓰인다.)
  useEffect(() => {
    if (status !== "authenticated") return;
    let active = true;
    getMe()
      .then((me) => { if (active) setNickname(me.nickname); })
      .catch(() => { if (active) setNickname(null); /* 실패해도 헤더는 뜨게 둔다. */ });
    return () => { active = false; };
  }, [status]);

  const role = user?.role ?? null;
  // requiredRole로 자식 메뉴를 거르고, 남은 자식이 없고 자체 경로도 없는 부모 메뉴는 숨긴다.
  const visibleNavigation = publicNavigation
    .map((item) => ({ ...item, children: item.children?.filter((child) => isVisibleForRole(child, role)) }))
    .filter((item) => item.path || (item.children && item.children.length > 0));

  const go = (path: string) => {
    navigate(path);
    setMobileOpen(false);
  };
  const handleLogout = async () => {
    // logout()은 서버 요청이 실패해도 finally에서 로컬 인증 상태를 지운다.
    // 요청이 throw하더라도 로그아웃은 이미 성립했으니, 오류를 삼키고 항상 홈으로 이동한다.
    try {
      await logout();
    } catch {
      // 로그아웃 요청 실패는 무시 - 로컬 상태는 이미 정리됨
    }
    go("/");
  };

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-card/95 backdrop-blur">
      <div className="page-shell flex h-[72px] items-center justify-between gap-4">
        <HeaderLogo />

        <nav className="hidden items-center gap-1 lg:flex" aria-label="주요 메뉴">
          {visibleNavigation.map((item) =>
            item.children && item.children.length > 0 ? (
              <DropdownMenu
                key={item.label}
                label={item.label}
                items={item.children.map((child) => ({ label: child.label, onSelect: () => child.path && navigate(child.path) }))}
              />
            ) : (
              <TopNavLink key={item.label} to={item.path!} label={item.label} />
            ),
          )}
        </nav>

        <div className="hidden items-center gap-1 lg:flex">
          {status === "authenticated" && role ? (
            <>
              <button
                type="button"
                aria-label={`알림 ${unreadCount}건`}
                className="relative rounded-button p-2 text-muted hover:bg-page hover:text-ink"
                onClick={() => navigate("/notifications")}
              >
                <Bell size={19} />
                {unreadCount > 0 && <span className="absolute right-1 top-1 size-2 rounded-full bg-primary" />}
              </button>
              <ProfileMenu role={role} name={nickname ?? "내 계정"} onLogout={handleLogout} />
            </>
          ) : status === "unauthenticated" ? (
            <>
              <Button variant="ghost" onClick={() => navigate("/login")}>로그인</Button>
              <Button variant="primary" onClick={() => navigate("/signup")}>회원가입</Button>
            </>
          ) : null}
        </div>

        <button
          type="button"
          className="rounded-button p-2 text-ink hover:bg-page lg:hidden"
          aria-label={mobileOpen ? "메뉴 닫기" : "메뉴 열기"}
          aria-expanded={mobileOpen}
          onClick={() => setMobileOpen((value) => !value)}
        >
          {mobileOpen ? <X size={23} /> : <Menu size={23} />}
        </button>
      </div>

      {mobileOpen && (
        <div className="border-t border-line bg-card lg:hidden">
          <div className="page-shell py-2">
            <nav aria-label="모바일 주요 메뉴">
              {visibleNavigation.map((item) =>
                item.children && item.children.length > 0 ? (
                  <MobileSection key={item.label} item={item} onNavigate={go} />
                ) : (
                  <button
                    key={item.label}
                    type="button"
                    className="block w-full border-b border-line px-1 py-3 text-left text-sm font-bold"
                    onClick={() => item.path && go(item.path)}
                  >
                    {item.label}
                  </button>
                ),
              )}
            </nav>

            {status === "authenticated" && role ? (
              <>
                <div className="flex items-center justify-between py-4">
                  <button type="button" className="flex items-center gap-2 text-sm font-bold" onClick={() => go("/mypage")}>
                    <UserRound size={17} />
                    {nickname ?? "내 계정"}님
                  </button>
                  <button type="button" aria-label="알림 보기" className="relative p-2" onClick={() => go("/notifications")}>
                    <Bell size={19} />
                    {unreadCount > 0 && <span className="absolute right-1 top-1 size-2 rounded-full bg-primary" />}
                  </button>
                </div>
                {role === "EVENT_ADMIN" && (
                  <button type="button" className="mb-2 block w-full text-left text-sm font-bold" onClick={() => go("/fair-admin/fair")}>
                    박람회 관리자 콘솔
                  </button>
                )}
                {role === "SUPER_ADMIN" && (
                  <button type="button" className="mb-2 block w-full text-left text-sm font-bold" onClick={() => go("/admin")}>
                    최고 관리자 콘솔
                  </button>
                )}
                <button type="button" className="mb-3 flex items-center gap-2 text-sm text-muted" onClick={handleLogout}>
                  <LogOut size={16} />
                  로그아웃
                </button>
              </>
            ) : status === "unauthenticated" ? (
              <div className="flex gap-2 py-4">
                <Button variant="outline" className="flex-1" onClick={() => go("/login")}>로그인</Button>
                <Button variant="primary" className="flex-1" onClick={() => go("/signup")}>회원가입</Button>
              </div>
            ) : null}
          </div>
        </div>
      )}
    </header>
  );
}
