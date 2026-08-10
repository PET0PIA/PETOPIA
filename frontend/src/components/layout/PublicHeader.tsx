import { Bell, ChevronDown, LogOut, Menu, UserRound, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { publicNavigation } from "../../config/navigation";
import type { BusinessStatus, CurrentUser } from "../../types/domain";
import type { NavigationItem } from "../../config/navigation";
import petopiaLogoOriginal from "../../assets/petopia-logo-original.png";
import { currentUser } from "../../mocks/home";
import { getUnreadNotificationCount } from "../../api/notification";
import { useAuth } from "../../contexts/AuthContext";
import { DropdownMenu } from "../ui/DropdownMenu";

function useUnreadNotificationCount() {
  const [count, setCount] = useState(0);
  const { pathname } = useLocation();
  const { status } = useAuth();
  useEffect(() => {
    if (status !== "authenticated") {
      setCount(0);
      return;
    }
    let active = true;
    getUnreadNotificationCount()
      .then((value) => { if (active) setCount(value); })
      .catch(() => { if (active) setCount(0); /* 알림 배지는 조회 실패 시 0으로 유지한다. */ });
    return () => { active = false; };
  }, [pathname, status]);
  return count;
}

function matchesStatus(item: NavigationItem, businessStatus: BusinessStatus) {
  return !item.requiredBusinessStatus || item.requiredBusinessStatus === businessStatus;
}

function HeaderLogo() {
  return <Link to="/" className="flex shrink-0 items-center gap-3" aria-label="PETOPIA 홈"><img src={petopiaLogoOriginal} alt="" className="h-14 w-auto object-contain" /><span className="text-2xl font-black tracking-tight text-ink">PETOPIA</span></Link>;
}

function ProfileMenu({ user }: { user: CurrentUser }) {
  const navigate = useNavigate();
  const items = [
    { label: "마이페이지", path: "/mypage" }, { label: "내 예약 목록", path: "/reservations/me" },
    ...(user.businessStatus === "APPROVED" ? [{ label: "내 사업자 목록", path: "/businesses/me" }, { label: "참가 신청 현황", path: "/participations/me" }, { label: "내 부스 관리", path: "/booths/me" }] : []),
    { label: "로그아웃", path: "/" },
  ];
  return <DropdownMenu label={user.name} items={items.map(({ label, path }) => ({ label, onSelect: () => navigate(path) }))} />;
}

function MobileSection({ item, onNavigate, user }: { item: NavigationItem; onNavigate: (path: string) => void; user: CurrentUser }) {
  const [expanded, setExpanded] = useState(false);
  const children = item.children?.filter((child) => matchesStatus(child, user.businessStatus)) ?? [];
  return <div className="border-b border-line py-1"><button type="button" className="flex w-full items-center justify-between px-1 py-3 text-left text-sm font-bold" aria-expanded={expanded} onClick={() => setExpanded((value) => !value)}>{item.label}<ChevronDown size={17} className={expanded ? "rotate-180 transition-transform" : "transition-transform"} /></button>{expanded && <div className="pb-2 pl-3">{children.map((child) => <button className="block w-full py-2 text-left text-sm text-muted hover:text-primary-strong" key={child.label} type="button" onClick={() => child.path && onNavigate(child.path)}>{child.label}</button>)}</div>}</div>;
}

export function PublicHeader() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const navigate = useNavigate();
  const unreadCount = useUnreadNotificationCount();
  const visibleNavigation = publicNavigation.map((item) => ({ ...item, children: item.children?.filter((child) => matchesStatus(child, currentUser.businessStatus)) }));
  const go = (path: string) => { navigate(path); setMobileOpen(false); };
  return <header className="sticky top-0 z-40 border-b border-line bg-card/95 backdrop-blur"><div className="page-shell flex h-[72px] items-center justify-between gap-4"><HeaderLogo /><nav className="hidden items-center lg:flex">{visibleNavigation.map((item) => <DropdownMenu key={item.label} label={item.label} items={(item.children ?? []).map((child) => ({ label: child.label, onSelect: () => child.path && navigate(child.path) }))} />)}</nav><div className="hidden items-center gap-1 lg:flex"><button type="button" aria-label={`알림 ${unreadCount}건`} className="relative rounded-button p-2 text-muted hover:bg-page hover:text-ink" onClick={() => navigate("/notifications")}><Bell size={19} />{unreadCount > 0 && <span className="absolute right-1 top-1 size-2 rounded-full bg-primary" />}</button><ProfileMenu user={currentUser} /></div><button className="rounded-button p-2 text-ink hover:bg-page lg:hidden" type="button" aria-label={mobileOpen ? "메뉴 닫기" : "메뉴 열기"} aria-expanded={mobileOpen} onClick={() => setMobileOpen((value) => !value)}>{mobileOpen ? <X size={23} /> : <Menu size={23} />}</button></div>{mobileOpen && <div className="border-t border-line bg-card lg:hidden"><div className="page-shell py-2"><nav aria-label="모바일 주요 메뉴">{visibleNavigation.map((item) => <MobileSection key={item.label} item={item} user={currentUser} onNavigate={go} />)}</nav><div className="flex items-center justify-between py-4"><button type="button" className="flex items-center gap-2 text-sm font-bold" onClick={() => go("/mypage")}><UserRound size={17} />{currentUser.name}님</button><button type="button" aria-label="알림 보기" className="relative p-2" onClick={() => go("/notifications")}><Bell size={19} />{unreadCount > 0 && <span className="absolute right-1 top-1 size-2 rounded-full bg-primary" />}</button></div><button type="button" className="mb-3 flex items-center gap-2 text-sm text-muted" onClick={() => go("/")}><LogOut size={16} />로그아웃</button></div></div>}</header>;
}
