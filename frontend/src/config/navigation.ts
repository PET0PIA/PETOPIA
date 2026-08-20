import type { ComponentType } from "react";
import { Ban, BarChart3, Bell, Building2, CalendarDays, ClipboardCheck, ClipboardX, CreditCard, FileCheck2, Headset, Image, LayoutDashboard, ListOrdered, Map, Megaphone, MessageSquare, PlusCircle, QrCode, ReceiptText, RotateCcw, ScrollText, Settings2, Star, Store, Ticket, UsersRound } from "lucide-react";
import type { UserRole } from "../api/auth";

export interface NavigationItem {
  label: string;
  path?: string;
  icon?: ComponentType<{ size?: number; strokeWidth?: number; className?: string }>;
  children?: NavigationItem[];
  /** 지정하면 해당 role로 로그인한 사용자에게만 노출한다. 없으면 비로그인 포함 누구나. */
  requiredRole?: UserRole[];
}

export const publicNavigation: NavigationItem[] = [
  // 관람객 구역: 행사를 둘러보고 예매까지 한 목록에서. 예전엔 '행사 목록'+'티켓 예매'로 나눴지만,
  // 티켓 예매 목록이 행사 목록과 중복이라(목록의 '사전예약 중' 탭이 그 역할) 하나로 합쳤다.
  { label: "행사", path: "/fairs/upcoming" },
  { label: "소식·이벤트", path: "/news" },
  {
    // 비즈니스 구역: "일하러 온 사람"(주최자·업체·광고주)의 신청/문의 창구 3개.
    // 사업자(참가업체) 등록은 메뉴에 두지 않고, "부스 참가 신청" 흐름에서 미등록 시 등록으로 유도한다.
    label: "비즈니스",
    children: [
      { label: "박람회 개최 신청", path: "/fair-applications/new" },
      { label: "부스 참가 신청", path: "/participations/new" },
      { label: "광고 문의", path: "/advertising" },
    ],
  },
  // 관리자·부스 콘솔 진입은 공개 메뉴가 아니라, 로그인한 사용자의 역할에 따라 헤더의 "전환" 버튼으로
  // 노출한다(PublicHeader). 로그인 사용자의 "내 업무"(내 예약·내 행사 신청)는 프로필 드롭다운에 둔다.
];

// ── 콘솔(관리자/부스) 네비게이션 ────────────────────────────────────────────────
// 콘솔은 좌측 사이드바에 "영역 그룹" 형태로 메뉴를 편다(ConsoleChrome). 메뉴가 많아 영역별로 묶는다.
// children이 있는 항목은 그룹(섹션), path만 있는 항목은 단일 링크로 렌더된다.

export const superAdminNavigation: NavigationItem[] = [
  { label: "대시보드", path: "/admin/dashboard", icon: LayoutDashboard },
  {
    label: "행사 관리",
    icon: CalendarDays,
    children: [
      { label: "행사 등록 신청 검토", path: "/admin/fair-applications", icon: FileCheck2 },
      { label: "행사 안내 관리", path: "/admin/fairs", icon: Store },
      { label: "장소 관리", path: "/admin/venues", icon: Map },
      { label: "행사 취소 신청 처리", path: "/admin/cancellations", icon: Ban },
    ],
  },
  {
    label: "사업자 관리",
    icon: Building2,
    children: [
      { label: "사업자 심사", path: "/admin/businesses", icon: FileCheck2 },
    ],
  },
  {
    label: "결제·정산",
    icon: CreditCard,
    children: [
      { label: "결제 상세 조회", path: "/admin/payments", icon: CreditCard },
      { label: "결제 생성·확정", path: "/admin/payments/create", icon: PlusCircle },
      { label: "결제 목록", path: "/admin/payments/list", icon: ListOrdered },
      { label: "환불 조회", path: "/admin/refunds", icon: RotateCcw },
      { label: "정산·수수료율", path: "/admin/settlements", icon: ReceiptText },
    ],
  },
  {
    label: "콘텐츠·홍보",
    icon: Megaphone,
    children: [
      { label: "광고 배너 관리", path: "/admin/banners", icon: Image },
      { label: "광고 팝업 관리", path: "/admin/popups", icon: MessageSquare },
      { label: "공지사항 관리", path: "/admin/notices", icon: Bell },
    ],
  },
  {
    label: "고객지원",
    icon: Headset,
    children: [
      { label: "상담 문의", path: "/admin/chat", icon: Headset },
    ],
  },
  {
    label: "시스템",
    icon: Settings2,
    children: [
      { label: "관리자 계정 목록", path: "/admin/accounts", icon: UsersRound },
      { label: "감사 로그", path: "/admin/audit-logs", icon: ScrollText },
    ],
  },
];

export const fairAdminNavigation: NavigationItem[] = [
  {
    label: "행사 운영",
    icon: Settings2,
    children: [
      { label: "행사 정보 관리", path: "/fair-admin/fair", icon: Settings2 },
      { label: "현장예매 설정", path: "/fair-admin/onsite-sales", icon: Store },
      { label: "부스 배치 관리", path: "/fair-admin/booths", icon: Map },
      { label: "모집 공고 관리", path: "/fair-admin/recruit-notice", icon: Megaphone },
      { label: "참가업체 신청 관리", path: "/fair-admin/participations", icon: ClipboardCheck },
      { label: "참가 취소 신청 검토", path: "/fair-admin/cancellation-requests", icon: ClipboardX },
    ],
  },
  {
    label: "예약·입장",
    icon: Ticket,
    children: [
      { label: "예약 현황", path: "/fair-admin/reservations", icon: Ticket },
      { label: "QR 입장 스캔", path: "/fair-admin/qr", icon: QrCode },
      { label: "방문 통계", path: "/fair-admin/statistics", icon: BarChart3 },
      { label: "리뷰 관리", path: "/fair-admin/reviews", icon: Star },
    ],
  },
  {
    label: "정산·기타",
    icon: CreditCard,
    children: [
      { label: "개설비 결제", path: "/payments/fair-opening-fee", icon: CreditCard },
      { label: "행사 취소 신청", path: "/fair-admin/cancellation", icon: Ban },
    ],
  },
];

// 부스(참여기업) 콘솔 - VENDOR 역할. 흩어져 있던 참가업체 기능을 한 콘솔로 모은다.
// "새 부스 참가 신청"은 공개 신청 퍼널(/participations/new)로 연결한다(콘솔 밖 공용 화면).
export const vendorNavigation: NavigationItem[] = [
  { label: "내 사업자", path: "/vendor/businesses", icon: Building2 },
  { label: "내 부스", path: "/vendor/booths", icon: Store },
  {
    label: "부스 참가",
    icon: ClipboardCheck,
    children: [
      { label: "새 부스 참가 신청", path: "/participations/new", icon: PlusCircle },
      { label: "내 참가 신청·현황", path: "/vendor/participations", icon: ListOrdered },
    ],
  },
  { label: "부스 방문 스캔", path: "/vendor/scan", icon: QrCode },
];

/**
 * 그룹(children) 구조의 콘솔 네비를 라우팅·조회용 평탄 목록으로 편다.
 * path가 있는 항목(단일 링크 + 그룹의 자식)만 남긴다. 콘솔 라우트의 폴백 생성과
 * 현재 경로→라벨 조회(AppRouter)에서 쓴다.
 */
export function flattenNavigation(items: NavigationItem[]): NavigationItem[] {
  return items.flatMap((item) => (item.children?.length ? item.children : item.path ? [item] : []));
}
