import { Home, LogOut, Menu, X } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import type { NavigationItem } from "../../config/navigation";
import { useAuth } from "../../contexts/AuthContext";
import { FairSwitcher } from "../fair-admin/FairSwitcher";
import { getMe } from "../../api/user";
import petopiaLogoOriginal from "../../assets/logo/petopiaLOGO.png";

interface ConsoleChromeProps {
  /** 왼쪽 사이드바에 펼칠 콘솔 네비게이션(그룹 = 섹션, path만 있는 항목 = 단일 링크). */
  navigation: NavigationItem[];
  /** 로고 옆 배지로 보여줄 콘솔 이름. 예: "최고 관리자", "박람회 관리자", "부스 관리자". */
  consoleLabel: string;
  children: ReactNode;
}

// 사이드바 링크 하나. 현재 경로면 배경으로 강조한다.
function SideLink({ item, onNavigate }: { item: NavigationItem; onNavigate: () => void }) {
  const Icon = item.icon;
  return (
    <NavLink
      to={item.path ?? "#"}
      end
      onClick={onNavigate}
      className={({ isActive }) =>
        `flex items-center gap-3 rounded-button px-3 py-2.5 text-sm font-semibold transition ${isActive ? "bg-primary-soft text-primary-strong" : "text-muted hover:bg-page hover:text-ink"}`
      }
    >
      {Icon && <Icon size={18} />}
      {item.label}
    </NavLink>
  );
}

export function ConsoleChrome({ navigation, consoleLabel, children }: ConsoleChromeProps) {
  const [open, setOpen] = useState(false);
  const { logout } = useAuth();
  const navigate = useNavigate();

  // JWT엔 이름이 없어 /users/me로 닉네임을 한 번 가져온다(PublicHeader와 동일 패턴).
  const [nickname, setNickname] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    getMe()
      .then((me) => { if (active) setNickname(me.nickname); })
      .catch(() => {});
    return () => { active = false; };
  }, []);

  const closeSidebar = () => setOpen(false);

  async function handleLogout() {
    try {
      await logout();
    } catch {
      // 로그아웃 요청 실패는 무시 - 로컬 상태는 이미 정리됨
    }
    navigate("/");
  }

  return (
    <div className="min-h-screen bg-page">
      <header className="sticky top-0 z-40 border-b border-line bg-card">
        <div className="flex h-[72px] items-center justify-between gap-3 px-4 sm:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <button
              type="button"
              aria-label={open ? "메뉴 닫기" : "메뉴 열기"}
              aria-expanded={open}
              className="shrink-0 rounded-button p-2 text-ink hover:bg-page lg:hidden"
              onClick={() => setOpen((value) => !value)}
            >
              {open ? <X size={22} /> : <Menu size={22} />}
            </button>
            <Link to="/" className="flex shrink-0 items-center gap-2.5" aria-label="PETOPIA 홈">
              <img src={petopiaLogoOriginal} alt="" className="h-10 w-auto object-contain" />
              <span className="hidden text-xl font-black tracking-tight text-ink sm:inline">PETOPIA</span>
            </Link>
            <span className="hidden shrink-0 rounded-full bg-primary-soft px-2.5 py-1 text-xs font-bold text-primary-strong md:inline">
              {consoleLabel}
            </span>
            <FairSwitcher />
          </div>

          <div className="flex shrink-0 items-center gap-2 sm:gap-3">
            <Link
              to="/"
              className="inline-flex items-center gap-1.5 rounded-full border border-line px-3 py-1.5 text-sm font-semibold text-muted transition hover:border-primary hover:text-primary-strong"
            >
              <Home size={16} />
              <span className="hidden sm:inline">일반 화면으로</span>
            </Link>
            <span className="hidden text-sm font-bold text-ink sm:inline">{nickname ?? "내 계정"}</span>
            <button
              type="button"
              onClick={handleLogout}
              aria-label="로그아웃"
              className="rounded-button p-2 text-muted hover:bg-page hover:text-primary-strong"
            >
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </header>

      <div className="flex">
        <aside
          className={`fixed inset-y-[72px] left-0 z-30 w-64 overflow-y-auto border-r border-line bg-card p-4 transition-transform lg:sticky lg:top-[72px] lg:h-[calc(100vh-72px)] lg:translate-x-0 ${open ? "translate-x-0" : "-translate-x-full"}`}
        >
          <p className="mb-3 px-3 text-xs font-bold text-muted md:hidden">{consoleLabel}</p>
          <nav className="flex flex-col gap-1">
            {navigation.map((item) =>
              item.children?.length ? (
                <div key={item.label} className="mt-5 first:mt-0">
                  <p className="mb-1 px-3 text-xs font-bold uppercase tracking-wide text-muted">{item.label}</p>
                  {item.children.map((child) => (
                    <SideLink key={child.label} item={child} onNavigate={closeSidebar} />
                  ))}
                </div>
              ) : (
                <SideLink key={item.label} item={item} onNavigate={closeSidebar} />
              ),
            )}
          </nav>
        </aside>

        {open && (
          <button
            type="button"
            aria-label="메뉴 닫기"
            className="fixed inset-0 top-[72px] z-20 bg-ink/20 lg:hidden"
            onClick={closeSidebar}
          />
        )}

        <main className="min-w-0 flex-1 p-4 sm:p-6 lg:p-8">{children}</main>
      </div>
    </div>
  );
}
