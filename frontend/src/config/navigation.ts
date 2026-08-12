import type { ComponentType } from "react";
import { Ban, BarChart3, CalendarDays, ClipboardCheck, CreditCard, FileCheck2, LayoutDashboard, ListOrdered, Map, Megaphone, PlusCircle, QrCode, ReceiptText, RotateCcw, ScrollText, Settings2, Store, Ticket, UsersRound } from "lucide-react";
import type { BusinessStatus } from "../types/domain";

export interface NavigationItem {
  label: string;
  path?: string;
  icon?: ComponentType<{ size?: number; strokeWidth?: number }>;
  children?: NavigationItem[];
  requiredBusinessStatus?: BusinessStatus;
}

export const publicNavigation: NavigationItem[] = [
  { label: "행사 안내", children: [{ label: "지난 행사", path: "/fairs/past" }, { label: "예정 행사", path: "/fairs/upcoming" }, { label: "행사 신청", path: "/fair-applications/new" }, { label: "내 행사 신청 목록", path: "/fair-applications/me" }] },
  { label: "티켓 예매", children: [{ label: "예매 가능한 행사", path: "/tickets" }, { label: "행사별 참여 기업", path: "/businesses" }] },
  { label: "참여 업체", children: [{ label: "참여 부스 신청", path: "/participations/new" }, { label: "사업자 등록 신청", path: "/businesses/new", requiredBusinessStatus: "NOT_REGISTERED" }, { label: "사업자 등록 현황", path: "/businesses/status", requiredBusinessStatus: "PENDING" }, { label: "내 사업자 목록", path: "/businesses/me", requiredBusinessStatus: "APPROVED" }, { label: "참가 신청 현황", path: "/participations/me", requiredBusinessStatus: "APPROVED" }, { label: "내 부스 관리", path: "/booths/me", requiredBusinessStatus: "APPROVED" }, { label: "부스 방문 스캔", path: "/booths/scan", requiredBusinessStatus: "APPROVED" }] },
  // 로그인/역할 배선 전이라 MVP 화면 확인용으로 항상 노출한다. 인증 도입 시 역할(EVENT_ADMIN/SUPER_ADMIN) 게이팅으로 교체한다.
  { label: "관리자", children: [{ label: "박람회 관리자 콘솔", path: "/fair-admin/fair" }, { label: "최고 관리자 콘솔", path: "/admin" }] },
];

export const fairAdminNavigation: NavigationItem[] = [
  { label: "개설비 결제", path: "/payments/fair-opening-fee", icon: CreditCard },
  { label: "행사 정보 관리", path: "/fair-admin/fair", icon: Settings2 },
  { label: "현장예매 설정", path: "/fair-admin/onsite-sales", icon: Store },
  { label: "부스 배치 관리", path: "/fair-admin/booths", icon: Map },
  { label: "모집 공고 관리", path: "/fair-admin/recruit-notice", icon: Megaphone },
  { label: "참가업체 신청 관리", path: "/fair-admin/participations", icon: ClipboardCheck },
  { label: "예약 현황", path: "/fair-admin/reservations", icon: Ticket },
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
  { label: "정산·수수료율", path: "/admin/settlements", icon: ReceiptText },
  { label: "감사 로그", path: "/admin/audit-logs", icon: ScrollText },
];
