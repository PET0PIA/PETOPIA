import { LogOut, Menu, X } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import type { NavigationItem } from "../../config/navigation";
import { useAuth } from "../../contexts/AuthContext";
import { FairSwitcher } from "../fair-admin/FairSwitcher";
import { getMe } from "../../api/user";
import petopiaLogoOriginal from "../../assets/logo/PetopiaLOGO.png";

interface AdminChromeProps {
  navigation: NavigationItem[];
  children: ReactNode;
}

export function AdminChrome({ navigation, children }: AdminChromeProps) {
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

  async function handleLogout() {
    await logout();
    navigate("/");
  }

  return (
    <div className="min-h-screen bg-page">
      <header className="sticky top-0 z-40 border-b border-line bg-card">
        <div className="flex h-[72px] items-center justify-between gap-3 px-4 sm:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <Link to="/" className="flex shrink-0 items-center gap-3" aria-label="PETOPIA 홈">
              <img src={petopiaLogoOriginal} alt="" className="h-11 w-auto object-contain" />
              <span className="hidden text-2xl font-black tracking-tight text-ink sm:inline">PETOPIA</span>
            </Link>
            <FairSwitcher />
          </div>
          <div className="hidden shrink-0 items-center gap-4 text-sm sm:flex">
            <span className="font-bold">{nickname ?? "내 계정"}</span>
            <button onClick={handleLogout} className="flex items-center gap-1 text-muted hover:text-primary-strong" type="button">
              <LogOut size={16} />로그아웃
            </button>
          </div>
          <button
            type="button"
            aria-label={open ? "관리자 메뉴 닫기" : "관리자 메뉴 열기"}
            className="shrink-0 p-2 lg:hidden"
            onClick={() => setOpen((value) => !value)}
          >
            {open ? <X /> : <Menu />}
          </button>
        </div>
      </header>
      <div className="flex">
        <aside
          className={`fixed inset-y-[72px] left-0 z-30 w-64 border-r border-line bg-card p-4 transition-transform lg:sticky lg:top-[72px] lg:h-[calc(100vh-72px)] lg:translate-x-0 ${open ? "translate-x-0" : "-translate-x-full"}`}
        >
          <p className="mb-3 px-3 text-xs font-bold text-muted">운영 메뉴</p>
          <nav>
            {navigation.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.label}
                  to={item.path ?? "#"}
                  end
                  onClick={() => setOpen(false)}
                  className={({ isActive }) =>
                    `mb-1 flex items-center gap-3 rounded-button px-3 py-3 text-sm font-bold transition ${isActive ? "bg-primary-soft text-primary-strong" : "text-muted hover:bg-page hover:text-ink"}`
                  }
                >
                  {Icon && <Icon size={18} />}
                  {item.label}
                </NavLink>
              );
            })}
          </nav>
        </aside>
        {open && (
          <button
            type="button"
            aria-label="메뉴 닫기"
            className="fixed inset-0 top-[72px] z-20 bg-ink/20 lg:hidden"
            onClick={() => setOpen(false)}
          />
        )}
        <main className="min-w-0 flex-1 p-4 sm:p-6 lg:p-8">{children}</main>
      </div>
    </div>
  );
}
