import { Building2, CalendarDays, FileText, Heart, Home, PawPrint, Settings, Sparkles, Star, Store, UserRound } from "lucide-react";
import { useEffect, useState, type ComponentType } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { getMyBusinesses } from "../../api/business";
import { getMe, type UserMe } from "../../api/user";
import { Card } from "../ui/Card";

/** 마이페이지 공통 껍데기 - 프로필 카드 + 왼쪽 사이드바 + 하위 화면(Outlet).
 * 예약/부스/신청처럼 이미 있는 화면들을 /mypage 하위 라우트에서 그대로 재사용해,
 * 화면을 옮겨 다녀도 사이드바가 계속 붙어 있게 한다(화면 신설·기능 변경 없음). */

interface MyPageNavItem {
  label: string;
  path: string;
  icon: ComponentType<{ size?: number; className?: string }>;
  /** 경로가 정확히 일치할 때만 활성. 다른 항목의 상위 경로인 "마이페이지"에만 쓴다. */
  exact?: boolean;
}

interface MyPageNavGroup {
  /** null이면 제목 없이 단독 링크로 그린다. */
  label: string | null;
  items: MyPageNavItem[];
}

// 사업자 등록 신청은 신청 이력이 있을 때만 넣는다(아래 useHasAnyBusiness 참고).
const activityGroup: MyPageNavGroup = {
  label: "내 활동",
  items: [
    { label: "예약 내역", path: "/mypage/reservations", icon: CalendarDays },
    { label: "AI 부스 추천", path: "/mypage/recommendation", icon: Sparkles },
    { label: "방문한 부스", path: "/mypage/booths/visited", icon: Store },
    { label: "즐겨찾기", path: "/mypage/favorites", icon: Heart },
    { label: "내 리뷰", path: "/mypage/reviews", icon: Star },
  ],
};

const accountGroup: MyPageNavGroup = {
  label: "계정",
  items: [
    { label: "프로필 정보", path: "/mypage/edit", icon: UserRound },
    { label: "반려동물 정보", path: "/mypage/pets", icon: PawPrint },
    { label: "계정 설정", path: "/mypage/account", icon: Settings },
  ],
};

/** USER는 배지를 안 붙인다(일반 회원이 기본값이라 알려줄 정보가 없다). */
const roleBadgeLabel: Partial<Record<UserMe["role"], string>> = {
  VENDOR: "참가업체",
  EVENT_ADMIN: "박람회 관리자",
  SUPER_ADMIN: "최고 관리자",
};

// 사업자를 하나라도 등록한 적 있으면(대기/승인/반려/취소 무관) "사업자 등록 신청"을 노출한다.
// 승인돼서 VENDOR가 되면 부스 콘솔이 따로 열리지만, 그 전에도 자기 신청 결과는 봐야 하므로
// role과 별개로 판단한다. (지금은 PublicHeader에도 같은 로직이 있는데, 헤더 드롭다운을
// 줄이는 단계에서 헤더 쪽을 지운다.)
function useHasAnyBusiness() {
  const [hasBusiness, setHasBusiness] = useState(false);

  useEffect(() => {
    let active = true;
    getMyBusinesses()
      .then((businesses) => { if (active) setHasBusiness(businesses.length > 0); })
      .catch(() => { if (active) setHasBusiness(false); /* 조회 실패 시 메뉴를 숨긴다. */ });
    return () => { active = false; };
  }, []);

  return hasBusiness;
}

function SideLink({ item }: { item: MyPageNavItem }) {
  const Icon = item.icon;
  return (
    <NavLink
      to={item.path}
      end={item.exact}
      className={({ isActive }) =>
        `flex items-center gap-3 rounded-button px-3 py-2.5 text-sm font-semibold transition ${
          isActive ? "bg-primary-soft text-primary-strong" : "text-muted hover:bg-surface-alt hover:text-ink"
        }`
      }
    >
      <Icon size={18} className="shrink-0" />
      {item.label}
    </NavLink>
  );
}

/** 모바일용 가로 스크롤 탭. 좁은 화면에선 사이드바를 세로로 세울 자리가 없다. */
function MobileTabs({ groups }: { groups: MyPageNavGroup[] }) {
  const { pathname } = useLocation();
  const items = groups.flatMap((group) => group.items);
  return (
    <nav aria-label="마이페이지 메뉴" className="-mx-4 mb-6 overflow-x-auto px-4 lg:hidden">
      <div className="flex w-max gap-2">
        {items.map((item) => {
          const active = item.exact ? pathname === item.path : pathname.startsWith(item.path);
          return (
            <Link
              key={item.path}
              to={item.path}
              aria-current={active ? "page" : undefined}
              className={`shrink-0 rounded-pill border px-3.5 py-2 text-sm font-semibold transition ${
                active ? "border-primary bg-primary-soft text-primary-strong" : "border-line text-muted hover:text-ink"
              }`}
            >
              {item.label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}

function ProfileCard({ me }: { me: UserMe | null }) {
  const badge = me ? roleBadgeLabel[me.role] : undefined;
  // 프로필 사진 필드가 없는 계정 모델이라 아바타는 두지 않고 이름·역할·이메일만 보여준다.
  return (
    <Card className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between sm:p-7">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate text-xl font-extrabold tracking-tight text-ink">
            {me ? `${me.nickname}님` : "내 계정"}
          </p>
          {badge && (
            <span className="shrink-0 rounded-pill bg-primary-soft px-2.5 py-1 text-xs font-bold text-primary-strong">
              {badge}
            </span>
          )}
        </div>
        <p className="mt-1 truncate text-sm text-muted">{me?.email ?? "내 정보를 불러오는 중이에요…"}</p>
      </div>
      <Link
        to="/mypage/edit"
        className="inline-flex shrink-0 items-center justify-center gap-1.5 rounded-button border border-line px-4 py-2 text-sm font-bold text-ink transition hover:bg-surface-alt"
      >
        <UserRound size={16} />
        프로필 수정
      </Link>
    </Card>
  );
}

export function MyPageLayout() {
  const [me, setMe] = useState<UserMe | null>(null);
  const hasBusiness = useHasAnyBusiness();

  // getMe()가 실패해도 사이드바와 하위 화면은 그대로 뜨게 둔다. 여기서 페이지 전체를
  // 에러로 덮으면 예약 내역처럼 자기 데이터로 잘 뜨는 화면까지 같이 못 보게 된다.
  useEffect(() => {
    let active = true;
    getMe()
      .then((result) => { if (active) setMe(result); })
      .catch(() => { if (active) setMe(null); });
    return () => { active = false; };
  }, []);

  const applicationGroup: MyPageNavGroup = {
    label: "내 신청",
    items: [
      { label: "행사 신청 내역", path: "/mypage/fair-applications", icon: FileText },
      // 부스 참가 신청은 사업자 콘솔(/vendor/participations)에 있어 여기 넣지 않는다.
      ...(hasBusiness ? [{ label: "사업자 등록 신청", path: "/mypage/businesses", icon: Building2 }] : []),
    ],
  };

  const groups: MyPageNavGroup[] = [
    { label: null, items: [{ label: "마이페이지", path: "/mypage", icon: Home, exact: true }] },
    activityGroup,
    applicationGroup,
    accountGroup,
  ];

  return (
    <div className="page-shell py-8 lg:py-10">
      <ProfileCard me={me} />

      <div className="mt-6 flex gap-8">
        <aside className="hidden w-56 shrink-0 lg:block">
          <nav aria-label="마이페이지 메뉴" className="sticky top-[88px] flex flex-col gap-1">
            {groups.map((group, index) => (
              <div key={group.label ?? "root"} className={index === 0 ? "" : "mt-5"}>
                {group.label && (
                  <p className="mb-1 px-3 text-xs font-bold tracking-wide text-muted">{group.label}</p>
                )}
                {group.items.map((item) => (
                  <SideLink key={item.path} item={item} />
                ))}
              </div>
            ))}
          </nav>
        </aside>

        {/* 하위 화면들은 자기 PageContainer(.page-shell)를 그대로 갖고 있다. 이 안에서는
            바깥 page-shell이 이미 여백을 줬으니 안쪽 여백만 지운다 - 각 화면 파일은 안 건드린다. */}
        <div className="min-w-0 flex-1 [&_.page-shell]:px-0 [&_.page-shell]:py-0">
          <MobileTabs groups={groups} />
          <Outlet />
        </div>
      </div>
    </div>
  );
}
