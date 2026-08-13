import { BrowserRouter, Route, Routes, useLocation } from "react-router-dom";
import { fairAdminNavigation, superAdminNavigation } from "../config/navigation";
import { FairAdminLayout } from "../layouts/FairAdminLayout";
import { PublicLayout } from "../layouts/PublicLayout";
import { SuperAdminLayout } from "../layouts/SuperAdminLayout";
import { HomePage } from "../pages/home/HomePage";
import { FairApplicationNewPage } from "../pages/fair/FairApplicationNewPage";
import { FairApplicationEditPage } from "../pages/fair/FairApplicationEditPage";
import { MyFairApplicationsPage } from "../pages/fair/MyFairApplicationsPage";
import { MyFairApplicationDetailPage } from "../pages/fair/MyFairApplicationDetailPage";
import { FairUpcomingPage } from "../pages/fair/FairUpcomingPage";
import { FairPastPage } from "../pages/fair/FairPastPage";
import { TicketFairListPage } from "../pages/reservation/TicketFairListPage";
import { HallManagementPage } from "../pages/fair-admin/HallManagementPage";
import { BoothLayoutEditPage } from "../pages/fair-admin/BoothLayoutEditPage";
import { FairDateManagementPage } from "../pages/fair-admin/FairDateManagementPage";
import { OnsiteSalesPolicyPage } from "../pages/fair-admin/OnsiteSalesPolicyPage";
import { GateEntryScanPage } from "../pages/fair-admin/GateEntryScanPage";
import { ReservationStatusPage } from "../pages/fair-admin/ReservationStatusPage";
import { VisitStatisticsPage } from "../pages/fair-admin/VisitStatisticsPage";
import { BoothVisitStatsPage } from "../pages/fair-admin/BoothVisitStatsPage";
import { FairCancelRequestPage } from "../pages/fair-admin/FairCancelRequestPage";
import { AdminAccountsPage } from "../pages/admin/AdminAccountsPage";
import { AdminDashboardPage } from "../pages/admin/AdminDashboardPage";
import { FairApplicationReviewPage } from "../pages/admin/FairApplicationReviewPage";
import { FairCancelRequestReviewPage } from "../pages/admin/FairCancelRequestReviewPage";
import { PaymentDetailPage } from "../pages/payment/PaymentDetailPage";
import { PaymentCreatePage } from "../pages/payment/PaymentCreatePage";
import { PaymentListPage } from "../pages/payment/PaymentListPage";
import { PaymentFailPage, PaymentSuccessPage } from "../pages/payment/PaymentResultPage";
import { FairOpeningFeePaymentPage } from "../pages/payment/FairOpeningFeePaymentPage";
import { FairOpeningFeeSelectPage } from "../pages/payment/FairOpeningFeeSelectPage";
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
import { MyPage } from "../pages/mypage/MyPage";
import { EditProfilePage } from "../pages/mypage/EditProfilePage";
import { PasswordChangePage } from "../pages/mypage/PasswordChangePage";
import { PetFormPage } from "../pages/mypage/PetFormPage";
import { PetDetailPage } from "../pages/mypage/PetDetailPage";
import { BusinessRegisterPage } from "../pages/business/BusinessRegisterPage";
import { RecruitNoticeDetailPage } from "../pages/recruit-notice/RecruitNoticeDetailPage";
import { MyBusinessesPage } from "../pages/business/MyBusinessesPage";
import { BusinessDetailPage } from "../pages/business/BusinessDetailPage";
import { RecruitNoticeFormPage } from "../pages/recruit-notice/RecruitNoticeFormPage";
import { ApplicationSubmitPage } from "../pages/application/ApplicationSubmitPage";
import { MyApplicationsPage } from "../pages/application/MyApplicationsPage";
import { ApplicationDetailPage } from "../pages/application/ApplicationDetailPage";
import { ParticipationReviewPage } from "../pages/fair-admin/ParticipationReviewPage";
import { CancelRequestReviewPage } from "../pages/fair-admin/CancelRequestReviewPage";
import { BoothDetailPage } from "../pages/booth/BoothDetailPage";
import { BoothEditPage } from "../pages/booth/BoothEditPage";
import { BoothFavoritesPage } from "../pages/booth/BoothFavoritesPage";
import { ProtectedRoute } from "./ProtectedRoute";
import { BusinessesByFairPage } from "../pages/business/BusinessesByFairPage";
import { FairBoothsPage } from "../pages/fair/FairBoothsPage";
import { MyBoothsPage } from "../pages/booth/MyBoothsPage";
import { ApplicationEditPage } from "../pages/application/ApplicationEditPage";
import { ParticipationNewPage } from "../pages/application/ParticipationNewPage";
// TODO: 백엔드 role 가드 + 관리자 계정 발급 흐름 갖춰지면 fair-admin/admin도 ProtectedRoute로 감싸기

// 실제 화면이 구현된 경로는 여기서 제외하고 AppRouter에서 직접 라우팅한다.
const publicPages: Record<string, string> = {
  "/businesses/status": "사업자 등록 현황",
  "/news": "소식·이벤트",
  "/advertising": "광고 문의",
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
  "/fair-admin/recruit-notice",
  "/fair-admin/participations",
  // fair-admin 레이아웃 밖(PublicLayout)에 별도로 라우팅돼 있다 - fair-admin 하위
  // 폴백 라우트(AdminFallback)를 만들 필요가 없어서 여기 포함시켜 그 목록에서 뺀다.
  "/payments/fair-opening-fee",
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
    item.path !== "/admin/cancellations" &&
    item.path !== "/admin/accounts"
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
          <Route path="/fair-applications/me/:fairId/edit" element={<FairApplicationEditPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/mypage" element={<MyPage />} />
            <Route path="/mypage/edit" element={<EditProfilePage />} />
            <Route path="/mypage/password" element={<PasswordChangePage />} />
            <Route path="/mypage/pets/new" element={<PetFormPage />} />
            <Route path="/mypage/pets/:petId" element={<PetDetailPage />} />
            <Route path="/booths/:boothId/edit" element={<BoothEditPage />} />
            <Route path="/booths/favorites/me" element={<BoothFavoritesPage />} />
            <Route path="/booths/me" element={<MyBoothsPage />} />
          </Route>
          <Route path="/reservations/me" element={<MyReservationsPage />} />
          <Route path="/reservations/me/:reservationId" element={<ReservationDetailPage />} />
          <Route path="/fairs/upcoming" element={<FairUpcomingPage />} />
          <Route path="/fairs/past" element={<FairPastPage />} />
          {/* 비즈니스 ▾ "부스 참가 신청" 입구. 실제 목록(모집 중 행사)·미등록 유도 로직은 후속.
              지금은 준비중 화면에서 사업자 등록으로만 안내한다. */}
          <Route
            path="/fairs/recruiting"
            element={
              <PlaceholderPage
                title="부스 참가 신청"
                description="박람회에 부스로 참가 신청하는 화면이에요. 참가하려면 먼저 사업자(참가업체) 등록이 필요해요."
                actionTo="/businesses/new"
                actionLabel="사업자 등록하러 가기"
              />
            }
          />
          <Route path="/tickets" element={<TicketFairListPage />} />
          <Route path="/tickets/:fairId" element={<TicketReservationPage />} />
          {/* 토스 결제창이 돌아오는 착지 경로. src/payments/toss.ts의 successUrl·failUrl과 일치해야 한다. */}
          <Route path="/payments/success" element={<PaymentSuccessPage />} />
          <Route path="/payments/fail" element={<PaymentFailPage />} />
          <Route element={<ProtectedRoute roles={["EVENT_ADMIN", "SUPER_ADMIN"]} />}>
            <Route path="/payments/fair-opening-fee" element={<FairOpeningFeeSelectPage />} />
            <Route path="/payments/fair-opening-fee/:fairId" element={<FairOpeningFeePaymentPage />} />
          </Route>
          <Route path="/booths/scan" element={<BoothVisitScanPage />} />
          <Route path="/businesses" element={<BusinessesByFairPage />} />
          <Route path="/fairs/:fairId/booths" element={<FairBoothsPage />} />
          <Route path="/businesses/new" element={<BusinessRegisterPage />} />
          <Route path="/businesses/me" element={<MyBusinessesPage />} />
          <Route path="/businesses/:businessId" element={<BusinessDetailPage />} />
          <Route path="/participations/new" element={<ParticipationNewPage />} />
          <Route path="/fairs/:fairId/recruit-notice" element={<RecruitNoticeDetailPage />} />
          <Route path="/fairs/:fairId/apply" element={<ApplicationSubmitPage />} />
          <Route path="/participations/me" element={<MyApplicationsPage />} />
          <Route path="/participations/me/:applicationId" element={<ApplicationDetailPage />} />
          <Route path="/participations/me/:applicationId/edit" element={<ApplicationEditPage />} />
          <Route path="/booths/:boothId" element={<BoothDetailPage />} />
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
          <Route path="recruit-notice" element={<RecruitNoticeFormPage />} />
          <Route path="recruit-notice/:fairId" element={<RecruitNoticeFormPage />} />
          <Route path="participations" element={<ParticipationReviewPage />} />
          <Route path="cancellation-requests" element={<CancelRequestReviewPage />} />
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
          <Route path="accounts" element={<AdminAccountsPage />} />
          {superAdminFallbackNavigation.map((item) => (
            <Route key={item.path} path={item.path?.replace("/admin/", "")} element={<AdminFallback kind="super" />} />
          ))}
          <Route path="*" element={<AdminFallback kind="super" />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
