import { BrowserRouter, Route, Routes, useLocation } from "react-router-dom";
import { fairAdminNavigation, superAdminNavigation } from "../config/navigation";
import { FairAdminLayout } from "../layouts/FairAdminLayout";
import { PublicLayout } from "../layouts/PublicLayout";
import { SuperAdminLayout } from "../layouts/SuperAdminLayout";
import { HomePage } from "../pages/home/HomePage";
import { FairApplicationNewPage } from "../pages/fair/FairApplicationNewPage";
import { MyFairApplicationsPage } from "../pages/fair/MyFairApplicationsPage";
import { MyFairApplicationDetailPage } from "../pages/fair/MyFairApplicationDetailPage";
import { HallManagementPage } from "../pages/fair-admin/HallManagementPage";
import { BoothLayoutEditPage } from "../pages/fair-admin/BoothLayoutEditPage";
import { FairDateManagementPage } from "../pages/fair-admin/FairDateManagementPage";
import { OnsiteSalesPolicyPage } from "../pages/fair-admin/OnsiteSalesPolicyPage";
import { GateEntryScanPage } from "../pages/fair-admin/GateEntryScanPage";
import { ReservationStatusPage } from "../pages/fair-admin/ReservationStatusPage";
import { VisitStatisticsPage } from "../pages/fair-admin/VisitStatisticsPage";
import { BoothVisitStatsPage } from "../pages/fair-admin/BoothVisitStatsPage";
import { FairCancelRequestPage } from "../pages/fair-admin/FairCancelRequestPage";
import { AdminDashboardPage } from "../pages/admin/AdminDashboardPage";
import { FairApplicationReviewPage } from "../pages/admin/FairApplicationReviewPage";
import { FairCancelRequestReviewPage } from "../pages/admin/FairCancelRequestReviewPage";
import { PaymentDetailPage } from "../pages/payment/PaymentDetailPage";
import { PaymentCreatePage } from "../pages/payment/PaymentCreatePage";
import { PaymentListPage } from "../pages/payment/PaymentListPage";
import { PaymentFailPage, PaymentSuccessPage } from "../pages/payment/PaymentResultPage";
import { RefundPage } from "../pages/payment/RefundPage";
import { AuditLogPage } from "../pages/admin/AuditLogPage";
import { SettlementPage } from "../pages/admin/SettlementPage";
import { NotificationsPage } from "../pages/notification/NotificationsPage";
import { MyReservationsPage } from "../pages/reservation/MyReservationsPage";
import { ReservationDetailPage } from "../pages/reservation/ReservationDetailPage";
import { TicketReservationPage } from "../pages/reservation/TicketReservationPage";
import { BoothVisitScanPage } from "../pages/vendor/BoothVisitScanPage";
import { FairReservationsPage } from "../pages/fair-admin/FairReservationsPage";
import { NotFoundPage, PlaceholderPage } from "../pages/PlaceholderPage";
import { LoginPage } from "../pages/auth/LoginPage";
import { SignupPage } from "../pages/auth/SignupPage";
import { ForgotPasswordPage } from "../pages/auth/ForgotPasswordPage";
import { ResetPasswordPage } from "../pages/auth/ResetPasswordPage";
import { OAuthCallbackPage } from "../pages/auth/OAuthCallbackPage";
import { AdminLoginPage } from "../pages/auth/AdminLoginPage";
// TODO: 백엔드 role 가드 + 관리자 계정 발급 흐름 갖춰지면 fair-admin/admin을 ProtectedRoute로 감싸기
// import { ProtectedRoute } from "./ProtectedRoute";

// 실제 화면이 구현된 경로는 여기서 제외하고 AppRouter에서 직접 라우팅한다.
const publicPages: Record<string, string> = {
  "/fairs/past": "지난 행사",
  "/fairs/upcoming": "예정 행사",
  "/tickets": "티켓 예매",
  "/businesses": "행사별 참여 기업",
  "/businesses/new": "사업자 등록 신청",
  "/businesses/status": "사업자 등록 현황",
  "/businesses/me": "내 사업자 목록",
  "/participations/new": "참여 부스 신청",
  "/participations/me": "참가 신청 현황",
  "/booths/me": "내 부스 관리",
  "/mypage": "마이페이지",
  "/about": "서비스 소개",
  "/terms": "이용약관",
  "/privacy": "개인정보 처리방침",
  "/contact": "문의",
};
const fairAdminImplementedPaths = [
  "/fair-admin/booths",
  "/fair-admin/fair",
  "/fair-admin/onsite-sales",
  "/fair-admin/qr",
  "/fair-admin/reservations",
  "/fair-admin/statistics",
  "/fair-admin/cancellation",
];
const fairAdminFallbackNavigation = fairAdminNavigation.filter((item) => !fairAdminImplementedPaths.includes(item.path ?? ""));
const superAdminFallbackNavigation = superAdminNavigation.filter(
  (item) =>
    item.path !== "/admin" &&
    item.path !== "/admin/fair-applications" &&
    item.path !== "/admin/audit-logs" &&
    item.path !== "/admin/payments" &&
    item.path !== "/admin/payments/create" &&
    item.path !== "/admin/payments/list" &&
    item.path !== "/admin/refunds" &&
    item.path !== "/admin/settlements" &&
    item.path !== "/admin/cancellations"
);
function AdminFallback({ kind }: { kind: "fair" | "super" }) {
  const location = useLocation();
  const nav = kind === "fair" ? fairAdminNavigation : superAdminNavigation;
  const title = nav.find((item) => item.path === location.pathname)?.label ?? "관리자 메뉴";
  return <PlaceholderPage title={title} admin />;
}
export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        {/* PublicLayout(공개 헤더/푸터) 밖에 독립 라우트로 둔다 - 일반 홈페이지 어디에도 링크 안 걸린 숨겨진 진입점 */}
        <Route path="/admin/login" element={<AdminLoginPage />} />
        <Route element={<PublicLayout />}>
          <Route index element={<HomePage />} />
          <Route path="/fair-applications/new" element={<FairApplicationNewPage />} />
          <Route path="/fair-applications/me" element={<MyFairApplicationsPage />} />
          <Route path="/fair-applications/me/:fairId" element={<MyFairApplicationDetailPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
          <Route path="/reservations/me" element={<MyReservationsPage />} />
          <Route path="/reservations/me/:reservationId" element={<ReservationDetailPage />} />
          <Route path="/tickets/:fairId" element={<TicketReservationPage />} />
          {/* 토스 결제창이 돌아오는 착지 경로. src/payments/toss.ts의 successUrl·failUrl과 일치해야 한다. */}
          <Route path="/payments/success" element={<PaymentSuccessPage />} />
          <Route path="/payments/fail" element={<PaymentFailPage />} />
          <Route path="/booths/scan" element={<BoothVisitScanPage />} />
          {Object.entries(publicPages).map(([path, title]) => (
            <Route key={path} path={path} element={<PlaceholderPage title={title} />} />
          ))}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
        <Route path="fair-admin" element={<FairAdminLayout />}>
          <Route index element={<PlaceholderPage title="박람회 관리자" admin />} />
          <Route path="booths" element={<HallManagementPage />} />
          <Route path="booths/:fairId/:hallId" element={<BoothLayoutEditPage />} />
          <Route path="fair" element={<FairDateManagementPage />} />
          <Route path="onsite-sales" element={<OnsiteSalesPolicyPage />} />
          <Route path="qr" element={<GateEntryScanPage />} />
          <Route path="reservations" element={<ReservationStatusPage />} />
          <Route path="reservations/list" element={<FairReservationsPage />} />
          <Route path="statistics" element={<VisitStatisticsPage />} />
          <Route path="statistics/booths/:fairId" element={<BoothVisitStatsPage />} />
          <Route path="cancellation" element={<FairCancelRequestPage />} />
          {fairAdminFallbackNavigation.map((item) => (
            <Route key={item.path} path={item.path?.replace("/fair-admin/", "")} element={<AdminFallback kind="fair" />} />
          ))}
          <Route path="*" element={<AdminFallback kind="fair" />} />
        </Route>
        <Route path="admin" element={<SuperAdminLayout />}>
          <Route index element={<AdminDashboardPage />} />
          <Route path="dashboard/fairs/:fairId" element={<VisitStatisticsPage />} />
          <Route path="fair-applications" element={<FairApplicationReviewPage />} />
          <Route path="audit-logs" element={<AuditLogPage />} />
          <Route path="payments" element={<PaymentDetailPage />} />
          <Route path="payments/create" element={<PaymentCreatePage />} />
          <Route path="payments/list" element={<PaymentListPage />} />
          <Route path="refunds" element={<RefundPage />} />
          <Route path="settlements" element={<SettlementPage />} />
          <Route path="cancellations" element={<FairCancelRequestReviewPage />} />
          {superAdminFallbackNavigation.map((item) => (
            <Route key={item.path} path={item.path?.replace("/admin/", "")} element={<AdminFallback kind="super" />} />
          ))}
          <Route path="*" element={<AdminFallback kind="super" />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
