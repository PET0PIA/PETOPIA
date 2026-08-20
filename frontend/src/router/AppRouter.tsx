import { BrowserRouter, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { fairAdminNavigation, superAdminNavigation, vendorNavigation, flattenNavigation } from "../config/navigation";
import { ConsoleHome } from "../components/layout/ConsoleHome";
import { FairAdminLayout } from "../layouts/FairAdminLayout";
import { PublicLayout } from "../layouts/PublicLayout";
import { SuperAdminLayout } from "../layouts/SuperAdminLayout";
import { VendorLayout } from "../layouts/VendorLayout";
import { HomePage } from "../pages/home/HomePage";
import { FairApplicationNewPage } from "../pages/fair/FairApplicationNewPage";
import { FairApplicationEditPage } from "../pages/fair/FairApplicationEditPage";
import { MyFairApplicationsPage } from "../pages/fair/MyFairApplicationsPage";
import { MyFairApplicationDetailPage } from "../pages/fair/MyFairApplicationDetailPage";
import { FairListPage } from "../pages/fair/FairListPage";
import { FairDetailPage } from "../pages/fair/FairDetailPage";
import { FairReviewWizardPage } from "../pages/fair/FairReviewWizardPage";
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
import { AdminChatPage } from "../pages/admin/AdminChatPage";
import { AdminChatSettingsPage } from "../pages/admin/AdminChatSettingsPage";
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
import { AdminBannersPage } from "../pages/admin/AdminBannersPage";
import { AdminPopupsPage } from "../pages/admin/AdminPopupsPage";
import { AdminNoticesPage } from "../pages/admin/AdminNoticesPage";
import { NewsListPage } from "../pages/news/NewsListPage";
import { NewsDetailPage } from "../pages/news/NewsDetailPage";
import { MyVisitedBoothsPage } from "../pages/booth/MyVisitedBoothsPage";
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
import { MyReviewsPage } from "../pages/mypage/MyReviewsPage";
import { ReviewManagementPage } from "../pages/fair-admin/ReviewManagementPage";
import { ReviewDeletionPage } from "../pages/fair-admin/ReviewDeletionPage";
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
import { BoothRecommendationPage } from "../pages/fair/BoothRecommendationPage";
import { FairBoothsPage } from "../pages/fair/FairBoothsPage";
import { MyBoothsPage } from "../pages/booth/MyBoothsPage";
import { ApplicationEditPage } from "../pages/application/ApplicationEditPage";
import { ParticipationNewPage } from "../pages/application/ParticipationNewPage";
import { AdvertisingInquiryPage } from "../pages/advertising/AdvertisingInquiryPage";
import { BusinessReviewPage } from "../pages/admin/BusinessReviewPage";
// TODO: 백엔드 role 가드 + 관리자 계정 발급 흐름 갖춰지면 fair-admin/admin도 ProtectedRoute로 감싸기

// 실제 화면이 구현된 경로는 여기서 제외하고 AppRouter에서 직접 라우팅한다.
const publicPages: Record<string, string> = {
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
  "/fair-admin/reviews",
  "/fair-admin/reviews/manage",
  "/fair-admin/statistics",
  "/fair-admin/cancellation",
  "/fair-admin/recruit-notice",
  "/fair-admin/participations",
  "/fair-admin/cancellation-requests",
  // fair-admin 레이아웃 밖(PublicLayout)에 별도로 라우팅돼 있다 - fair-admin 하위
  // 폴백 라우트(AdminFallback)를 만들 필요가 없어서 여기 포함시켜 그 목록에서 뺀다.
  "/payments/fair-opening-fee",
];
const fairAdminFallbackNavigation = flattenNavigation(fairAdminNavigation).filter((item) => !fairAdminImplementedPaths.includes(item.path ?? ""));
const superAdminFallbackNavigation = flattenNavigation(superAdminNavigation).filter(
  (item) =>
    item.path !== "/admin/dashboard" &&
    item.path !== "/admin/fair-applications" &&
    item.path !== "/admin/audit-logs" &&
    item.path !== "/admin/payments" &&
    item.path !== "/admin/payments/create" &&
    item.path !== "/admin/payments/list" &&
    item.path !== "/admin/refunds" &&
    item.path !== "/admin/settlements" &&
    item.path !== "/admin/cancellations" &&
    item.path !== "/admin/accounts" &&
    item.path !== "/admin/banners" &&
    item.path !== "/admin/popups" &&
    item.path !== "/admin/chat" &&
    item.path !== "/admin/businesses"
);
function AdminFallback({ kind }: { kind: "fair" | "super" }) {
  const location = useLocation();
  const nav = kind === "fair" ? flattenNavigation(fairAdminNavigation) : flattenNavigation(superAdminNavigation);
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
          {/* 개최 신청은 로그인 필수(백엔드 POST /api/fairs = authenticated). 미로그인은 /login으로
              보냈다가 로그인 후 이 화면으로 복귀시킨다(폼을 채우다 제출 단계에서 막히는 걸 방지). */}
          <Route element={<ProtectedRoute />}>
            <Route path="/fair-applications/new" element={<FairApplicationNewPage />} />
          </Route>
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
            <Route path="/mypage/reviews" element={<MyReviewsPage />} />
            <Route path="/booths/:boothId/edit" element={<BoothEditPage />} />
            <Route path="/booths/favorites/me" element={<BoothFavoritesPage />} />
            <Route path="/booths/visited/me" element={<MyVisitedBoothsPage />} />
            {/* 부스 콘솔로 이관: 옛 경로는 콘솔로 리다이렉트(북마크·내부 링크 호환). */}
            <Route path="/booths/me" element={<Navigate to="/vendor/booths" replace />} />
          </Route>
          <Route path="/reservations/me" element={<MyReservationsPage />} />
          <Route path="/reservations/me/:reservationId" element={<ReservationDetailPage />} />
          {/* 행사 목록은 예정·진행·종료를 상태 배지로 구분하는 통합 목록 하나뿐이다.
              옛 "지난 행사" 경로(북마크·외부 링크)로 들어와도 같은 목록으로 넘긴다. */}
          <Route path="/fairs/upcoming" element={<FairListPage />} />
          <Route path="/fairs/past" element={<Navigate to="/fairs/upcoming" replace />} />
          {/* 행사 상세(공개). 목록 카드가 여기로 오고, '예매하기'는 /tickets/:fairId로 넘긴다.
              정적 경로(/fairs/upcoming 등)가 :fairId보다 우선 매칭되므로 충돌 없다. */}
          <Route path="/fairs/:fairId" element={<FairDetailPage />} />
          {/* 비즈니스 ▾ "부스 참가 신청" 입구. 실제 목록·신청 흐름은 /participations/new에 있어,
              옛 경로(북마크·외부 링크)로 들어와도 그 화면으로 넘긴다. */}
          <Route path="/fairs/recruiting" element={<Navigate to="/participations/new" replace />} />
          {/* 비즈니스 ▾ "광고 문의". 문의 전용 백엔드가 없어 이메일 안내(정적) 화면으로 연결한다. */}
          <Route path="/advertising" element={<AdvertisingInquiryPage />} />
          {/* 소식·이벤트. 목록에는 공지와 진행 중인 모집공고가 함께 나오고, 모집공고를 누르면
              기존 /fairs/:fairId/recruit-notice 화면으로 간다(서버가 linkPath로 정해준다). */}
          <Route path="/news" element={<NewsListPage />} />
          <Route path="/news/:noticeId" element={<NewsDetailPage />} />
          <Route path="/tickets/:fairId" element={<TicketReservationPage />} />
          {/* 토스 결제창이 돌아오는 착지 경로. src/payments/toss.ts의 successUrl·failUrl과 일치해야 한다. */}
          <Route path="/payments/success" element={<PaymentSuccessPage />} />
          <Route path="/payments/fail" element={<PaymentFailPage />} />
          <Route element={<ProtectedRoute roles={["EVENT_ADMIN", "SUPER_ADMIN"]} />}>
            <Route path="/payments/fair-opening-fee" element={<FairOpeningFeeSelectPage />} />
            <Route path="/payments/fair-opening-fee/:fairId" element={<FairOpeningFeePaymentPage />} />
          </Route>
          <Route path="/booths/scan" element={<Navigate to="/vendor/scan" replace />} />
          <Route path="/businesses" element={<BusinessesByFairPage />} />
          <Route path="/fairs/:fairId/booths" element={<FairBoothsPage />} />
          {/* 부스 추천 + 동선 추천(같은 입력으로 API 2개를 호출해 탭으로 결과를 나눠 보여준다). */}
          <Route path="/fairs/:fairId/booth-recommendations" element={<BoothRecommendationPage />} />
          {/* 태그 기반 통합 리뷰(V39) 작성 마법사. 로그인 필요. */}
          <Route element={<ProtectedRoute />}>
            <Route path="/fairs/:fairId/reviews/new" element={<FairReviewWizardPage />} />
          </Route>
          <Route path="/businesses/new" element={<BusinessRegisterPage />} />
          <Route path="/businesses/me" element={<MyBusinessesPage />} />
          <Route path="/businesses/:businessId" element={<BusinessDetailPage />} />
          <Route path="/participations/new" element={<ParticipationNewPage />} />
          <Route path="/fairs/:fairId/recruit-notice" element={<RecruitNoticeDetailPage />} />
          {/* 참가 신청서도 로그인 필수. 미로그인은 /login으로 보냈다가 복귀. 사업자 미등록은
              페이지 안에서 /businesses/new로 보내고(returnTo 포함) 등록 후 다시 이 화면으로 돌아온다. */}
          <Route element={<ProtectedRoute />}>
            <Route path="/fairs/:fairId/apply" element={<ApplicationSubmitPage />} />
          </Route>
          <Route path="/participations/me" element={<Navigate to="/vendor/participations" replace />} />
          <Route path="/participations/me/:applicationId" element={<ApplicationDetailPage />} />
          <Route path="/participations/me/:applicationId/edit" element={<ApplicationEditPage />} />
          <Route path="/booths/:boothId" element={<BoothDetailPage />} />
          {Object.entries(publicPages).map(([path, title]) => (
            <Route key={path} path={path} element={<PlaceholderPage title={title} />} />
          ))}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
        {/* 박람회 관리자 콘솔 - EVENT_ADMIN(+ 상위 SUPER_ADMIN) 전용. URL 직접 진입도 role로 가드한다. */}
        <Route element={<ProtectedRoute roles={["EVENT_ADMIN", "SUPER_ADMIN"]} />}>
          <Route path="fair-admin" element={<FairAdminLayout />}>
            <Route
              index
              element={
                <ConsoleHome
                  consoleLabel="박람회 관리자"
                  description="담당 행사의 운영·예약·정산을 여기서 관리해요. 상단 '관리 행사'에서 행사를 먼저 골라 주세요."
                  navigation={fairAdminNavigation}
                />
              }
            />
            <Route path="booths" element={<HallManagementPage />} />
            <Route path="booths/:fairId/:hallId" element={<BoothLayoutEditPage />} />
            <Route path="fair" element={<FairDateManagementPage />} />
            <Route path="onsite-sales" element={<OnsiteSalesPolicyPage />} />
            <Route path="qr" element={<GateEntryScanPage />} />
            <Route path="reservations" element={<ReservationStatusPage />} />
            <Route path="reservations/list" element={<FairReservationsPage />} />
            <Route path="reviews" element={<ReviewManagementPage />} />
            <Route path="reviews/manage" element={<ReviewDeletionPage />} />
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
        </Route>
        {/* 최고 관리자 콘솔 - SUPER_ADMIN 전용 */}
        <Route element={<ProtectedRoute roles={["SUPER_ADMIN"]} />}>
          <Route path="admin" element={<SuperAdminLayout />}>
            <Route
              index
              element={
                <ConsoleHome
                  consoleLabel="최고 관리자"
                  description="전체 행사 운영·결제·정산·시스템을 여기서 관리해요."
                  navigation={superAdminNavigation}
                />
              }
            />
            <Route path="dashboard" element={<AdminDashboardPage />} />
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
            {/* 콘텐츠·홍보 */}
            <Route path="banners" element={<AdminBannersPage />} />
            <Route path="popups" element={<AdminPopupsPage />} />
            <Route path="notices" element={<AdminNoticesPage />} />
            {/* 고객지원 */}
            <Route path="chat" element={<AdminChatPage />} />
            <Route path="chat/settings" element={<AdminChatSettingsPage />} />
            {/* 사업자 관리 */}
            <Route path="businesses" element={<BusinessReviewPage />} />
            {superAdminFallbackNavigation.map((item) => (
              <Route key={item.path} path={item.path?.replace("/admin/", "")} element={<AdminFallback kind="super" />} />
            ))}
            <Route path="*" element={<AdminFallback kind="super" />} />
          </Route>
        </Route>
        {/* 부스(참여기업) 콘솔 - VENDOR 전용. 흩어져 있던 참가업체 기능을 한 콘솔로 모은다. */}
        <Route element={<ProtectedRoute roles={["VENDOR"]} />}>
          <Route path="vendor" element={<VendorLayout />}>
            <Route
              index
              element={
                <ConsoleHome
                  consoleLabel="부스 관리자"
                  description="사업자·부스·참가 신청과 부스 방문 스캔을 한곳에서 관리해요."
                  navigation={vendorNavigation}
                />
              }
            />
            <Route path="businesses" element={<MyBusinessesPage />} />
            <Route path="booths" element={<MyBoothsPage />} />
            <Route path="participations" element={<MyApplicationsPage />} />
            <Route path="scan" element={<BoothVisitScanPage />} />
            {/* 부스 콘솔은 사이드바 메뉴 전부가 구현돼 있어, 여기 닿는 경우는 잘못된 주소뿐이다.
                그래서 다른 콘솔처럼 자리표시(PlaceholderPage)를 보여주는 대신 콘솔 홈으로 되돌린다
                (없으면 최상위 /* 에 걸려 공개 레이아웃 404로 콘솔 밖으로 튕겨 나간다). */}
            <Route path="*" element={<Navigate to="/vendor" replace />} />
          </Route>
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
