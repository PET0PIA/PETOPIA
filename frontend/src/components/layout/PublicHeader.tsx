import { Bell, ChevronDown, LayoutDashboard, LogOut, Menu, UserRound, X } from "lucide-react";
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
import { getMyBusinesses } from "../../api/business";

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

function useRoleSync() {
  const { status, refreshUser } = useAuth();
  const { pathname } = useLocation();

  // 사업자 취소로 role이 VENDOR -> USER로 바뀌어도 기존 JWT는 만료 전까지
  // 그대로 유효해서, 콘솔 링크가 새로고침 전까지 안 사라지는 문제가 있었다.
  // 페이지 이동마다 토큰을 재발급받아 role을 다시 읽어와 이를 보정한다.
  useEffect(() => {
    if (status !== "authenticated") return;
    refreshUser();
  }, [status, pathname]);
}

// requiredRole이 지정된 메뉴는 그 role로 로그인했을 때만 보인다. 없으면 비로그인 포함 누구나.
function isVisibleForRole(item: NavigationItem, role: UserRole | null) {
  if (!item.requiredRole) return true;
  return role != null && item.requiredRole.includes(role);
}

// 로그인 사용자의 역할별 콘솔 진입 정보. 헤더의 "전환" 버튼으로 노출한다.
// USER(및 비로그인)는 콘솔이 없어 null.
function consoleEntryForRole(role: UserRole | null): { label: string; path: string } | null {
  switch (role) {
    case "SUPER_ADMIN":
      return { label: "관리자 콘솔", path: "/admin" };
    case "EVENT_ADMIN":
      return { label: "박람회 관리", path: "/fair-admin" };
    case "VENDOR":
      return { label: "부스 관리", path: "/vendor" };
    default:
      return null;
  }
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

// 데스크톱 프로필 드롭다운과 모바일 메뉴가 함께 쓰는 개인 계정 항목. 한쪽에만 추가하면
// 화면 크기에 따라 못 들어가는 화면이 생기므로(실제로 "내 방문 부스"가 모바일에서
// 빠져 있었다) 목록을 여기 한 곳에만 둔다. 마이페이지·로그아웃은 두 메뉴에서 생김새가
// 달라(모바일은 아이콘 버튼) 각자 그린다.
const accountMenuItems: { label: string; path: string }[] = [
  { label: "내 예약 목록", path: "/reservations/me" },
  { label: "내 행사 신청 목록", path: "/fair-applications/me" },
  { label: "내 결제 내역", path: "/payments/me" },
  { label: "내 방문 부스", path: "/booths/visited/me" },
  { label: "즐겨찾기 부스", path: "/booths/favorites/me" },
];

// 사업자를 하나라도 등록한 적 있으면(대기/승인/반려/취소 상태 무관) "사업자 등록 현황"을
// 노출한다. 승인돼서 VENDOR가 되면 콘솔 전환 버튼(consoleEntryForRole)이 따로 뜨지만,
// 그 전에도 자기 신청이 어떻게 됐는지 확인할 방법은 있어야 해서 role과 별개로 둔다.
function useHasAnyBusiness() {
  const [hasBusiness, setHasBusiness] = useState(false);
  const { status } = useAuth();
  const { pathname } = useLocation();

  useEffect(() => {
    if (status !== "authenticated") {
      setHasBusiness(false);
      return;
    }
    let active = true;
    getMyBusinesses()
      .then((businesses) => { if (active) setHasBusiness(businesses.length > 0); })
      .catch(() => { if (active) setHasBusiness(false); });
    return () => { active = false; };
  }, [status, pathname]);

  return hasBusiness;
}

// 로그인한 사용자의 프로필 메뉴. 역할에 따라 사업자 메뉴/관리자 콘솔 진입이 더해진다.
// ProfileMenu - accountMenuItems를 prop으로 받도록 변경
function ProfileMenu({ name, accountMenuItems, onLogout }: { name: string; accountMenuItems: { label: string; path: string }[]; onLogout: () => void }) {
  const navigate = useNavigate();
  // 역할별 콘솔·업무 진입은 헤더의 "전환" 버튼(consoleEntryForRole)으로 옮겼다.
  // 프로필 메뉴엔 누구에게나 공통인 개인 계정 항목만 둔다.
  // 사업자 등록 현황 메뉴는 한 번이라도 사업자 신청을 했을 경우 나오는 메뉴이다.
  const items: { label: string; onSelect: () => void }[] = [
    { label: "마이페이지", onSelect: () => navigate("/mypage") },
    ...accountMenuItems.map(({ label, path }) => ({ label, onSelect: () => navigate(path) })),
    { label: "로그아웃", onSelect: onLogout },
  ];
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
  useRoleSync();
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
  const consoleEntry = consoleEntryForRole(role);
  const hasBusiness = useHasAnyBusiness();
  const visibleAccountMenuItems = (hasBusiness && role !== "VENDOR")
    ? [...accountMenuItems.slice(0, 2), { label: "사업자 등록 현황", path: "/businesses/me" }, ...accountMenuItems.slice(2)]
    : accountMenuItems;
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
              <ProfileMenu name={nickname ?? "내 계정"} accountMenuItems={visibleAccountMenuItems} onLogout={handleLogout} />
              {/* 역할별 콘솔 전환 버튼 - 프로필 바로 오른쪽에 둔다. */}
              {consoleEntry && (
                <button
                  type="button"
                  onClick={() => navigate(consoleEntry.path)}
                  className="inline-flex items-center gap-1.5 rounded-full bg-primary-soft px-3.5 py-2 text-sm font-bold text-primary-strong transition hover:opacity-80"
                >
                  <LayoutDashboard size={16} />
                  {consoleEntry.label}
                </button>
              )}
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
                {consoleEntry && (
                  <button
                    type="button"
                    onClick={() => go(consoleEntry.path)}
                    className="mb-3 inline-flex w-full items-center justify-center gap-1.5 rounded-full bg-primary-soft px-4 py-2.5 text-sm font-bold text-primary-strong hover:opacity-80"
                  >
                    <LayoutDashboard size={16} />
                    {consoleEntry.label}
                  </button>
                )}
                {visibleAccountMenuItems.map(({ label, path }) => (
                  <button key={path} type="button" className="mb-2 block w-full text-left text-sm font-bold" onClick={() => go(path)}>
                    {label}
                  </button>
                ))}
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
