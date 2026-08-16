import type { ComponentType } from "react";
import { Ban, BarChart3, CalendarDays, ClipboardCheck, CreditCard, FileCheck2, LayoutDashboard, ListOrdered, Map, Megaphone, MessageSquare, PlusCircle, QrCode, ReceiptText, RotateCcw, ScrollText, Settings2, Star, Store, Ticket, UsersRound } from "lucide-react";
import type { UserRole } from "../api/auth";

export interface NavigationItem {
  label: string;
  path?: string;
  icon?: ComponentType<{ size?: number; strokeWidth?: number }>;
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
  // 관리자 콘솔 진입은 공개 메뉴가 아니라, 로그인한 admin의 프로필 드롭다운에서 역할별로 노출한다(PublicHeader).
  // 로그인 사용자의 "내 업무"(내 예약·내 사업자·부스 방문 스캔·내 행사 신청)도 프로필 드롭다운으로 옮겼다.
  // 최고 관리자 로그인 입구는 푸터의 작은 링크(PublicLayout)로 둔다.
];

export const fairAdminNavigation: NavigationItem[] = [
  { label: "개설비 결제", path: "/payments/fair-opening-fee", icon: CreditCard },
  { label: "행사 정보 관리", path: "/fair-admin/fair", icon: Settings2 },
  { label: "현장예매 설정", path: "/fair-admin/onsite-sales", icon: Store },
  { label: "부스 배치 관리", path: "/fair-admin/booths", icon: Map },
  { label: "모집 공고 관리", path: "/fair-admin/recruit-notice", icon: Megaphone },
  { label: "참가업체 신청 관리", path: "/fair-admin/participations", icon: ClipboardCheck },
  { label: "예약 현황", path: "/fair-admin/reservations", icon: Ticket },
  { label: "리뷰 관리", path: "/fair-admin/reviews", icon: Star },
  { label: "QR 입장 스캔", path: "/fair-admin/qr", icon: QrCode },
  { label: "방문 통계", path: "/fair-admin/statistics", icon: BarChart3 },
  { label: "행사 취소 신청", path: "/fair-admin/cancellation", icon: Ban },
];

export const superAdminNavigation: NavigationItem[] = [
  { label: "전체 운영 대시보드", path: "/admin", icon: LayoutDashboard },
  { label: "행사 등록 신청 검토", path: "/admin/fair-applications", icon: FileCheck2 },
  { label: "결제 상세 조회", path: "/admin/payments", icon: CreditCard },
  { label: "결제 생성·확정", path: "/admin/payments/create", icon: PlusCircle },
  { label: "결제 목록", path: "/admin/payments/list", icon: ListOrdered },
  { label: "환불 조회", path: "/admin/refunds", icon: RotateCcw },
  { label: "행사 취소 신청 처리", path: "/admin/cancellations", icon: CalendarDays },
  { label: "행사 안내 관리", path: "/admin/fairs", icon: Store },
  { label: "장소 관리", path: "/admin/venues", icon: Map },
  { label: "관리자 계정 목록", path: "/admin/accounts", icon: UsersRound },
  { label: "상담 문의", path: "/admin/chat", icon: MessageSquare },
  { label: "정산·수수료율", path: "/admin/settlements", icon: ReceiptText },
  { label: "감사 로그", path: "/admin/audit-logs", icon: ScrollText },
];
