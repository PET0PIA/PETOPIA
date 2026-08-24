import { BrowserRouter, Navigate, Route, Routes, useLocation, useParams } from "react-router-dom";
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
import { AdminFairReservationStatusPage } from "../pages/admin/AdminFairReservationStatusPage";
import { FairApplicationReviewPage } from "../pages/admin/FairApplicationReviewPage";
import { FairCancelRequestReviewPage } from "../pages/admin/FairCancelRequestReviewPage";
import { PaymentDetailPage } from "../pages/payment/PaymentDetailPage";
import { PaymentListPage } from "../pages/payment/PaymentListPage";
import { PaymentFailPage, PaymentSuccessPage } from "../pages/payment/PaymentResultPage";
import { FairOpeningFeePaymentPage } from "../pages/payment/FairOpeningFeePaymentPage";
import { FairOpeningFeeSelectPage } from "../pages/payment/FairOpeningFeeSelectPage";
import { AuditLogPage } from "../pages/admin/AuditLogPage";
import { SettlementPage } from "../pages/admin/SettlementPage";
import { NotificationsPage } from "../pages/notification/NotificationsPage";
import { MyReservationsPage } from "../pages/reservation/MyReservationsPage";
import { ReservationDetailPage } from "../pages/reservation/ReservationDetailPage";
import { TicketReservationPage } from "../pages/reservation/TicketReservationPage";
import { BoothVisitScanPage } from "../pages/vendor/BoothVisitScanPage";
import { NotFoundPage, PlaceholderPage } from "../pages/PlaceholderPage";
import { AboutPage } from "../pages/about/AboutPage";
import { ContactPage } from "../pages/contact/ContactPage";
import { PrivacyPage, TermsPage } from "../pages/legal/LegalPages";
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
import { MyPageLayout } from "../components/mypage/MyPageLayout";
import { MyPageHome } from "../pages/mypage/MyPageHome";
import { MyPetsPage } from "../pages/mypage/MyPetsPage";
import { AccountSettingsPage } from "../pages/mypage/AccountSettingsPage";
import { EditProfilePage } from "../pages/mypage/EditProfilePage";
import { PasswordChangePage } from "../pages/mypage/PasswordChangePage";
import { AdminAccountSettingsPage } from "../pages/mypage/AdminAccountSettingsPage";
import { PetFormPage } from "../pages/mypage/PetFormPage";
import { PetDetailPage } from "../pages/mypage/PetDetailPage";
import { MyReviewsPage } from "../pages/mypage/MyReviewsPage";
import { MyRecommendationPage } from "../pages/mypage/MyRecommendationPage";
import { FairStatsPage } from "../pages/fair-admin/FairStatsPage";
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
import { FairPaymentSettlementPage } from "../pages/fair-admin/FairPaymentSettlementPage";
import { CancelRequestReviewPage } from "../pages/fair-admin/CancelRequestReviewPage";
import { BoothDetailPage } from "../pages/booth/BoothDetailPage";
import { BoothEditPage } from "../pages/booth/BoothEditPage";
import { BoothStatsPage } from "../pages/booth/BoothStatsPage";
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

/**
 * 콘솔 사이드바에 메뉴는 있지만 화면이 아직 없는 경로에서 "준비 중" 자리표시를 보여준다.
 *
 * 각 콘솔 맨 아래의 <Route path="*">가 이 역할을 하므로, "구현된 경로 목록"을 따로 들고
 * 껍데기 라우트를 미리 만들어 둘 필요가 없다(2026-08-23 정리). 예전에는 그 목록과 실제
 * 라우트가 어긋나서 같은 경로(/admin/notices)가 두 번 등록됐고, 먼저 선언된 쪽이 이기는
 * 순서 의존 버그가 있었다. 이제는 메뉴만 추가하고 화면을 안 만들면 자동으로 여기에 걸린다.
 */
function AdminFallback({ kind }: { kind: "fair" | "super" }) {
  const location = useLocation();
  const nav = kind === "fair" ? flattenNavigation(fairAdminNavigation) : flattenNavigation(superAdminNavigation);
  const title = nav.find((item) => item.path === location.pathname)?.label ?? "관리자 메뉴";
  return <PlaceholderPage title={title} admin />;
}

// 개설비 결제 상세(옛 경로 "/payments/fair-opening-fee/:fairId")의 리다이렉트 전용 - Navigate는
// :fairId를 직접 못 채우므로 fairId를 읽어 새 경로("/fair-admin/payments/fair-opening-fee/:fairId")로 넘겨준다.
function FairOpeningFeeLegacyRedirect() {
  const { fairId } = useParams();
  return <Navigate to={`/fair-admin/payments/fair-opening-fee/${fairId}`} replace />;
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        {/* PublicLayout(공개 헤더/푸터) 밖에 독립 라우트로 둔다 - 일반 홈페이지 어디에도 링크 안 걸린 숨겨진 진입점 */}
        <Route element={<PublicLayout />}>
          <Route index element={<HomePage />} />
          {/* 개최 신청은 로그인 필수(백엔드 POST /api/fairs = authenticated). 미로그인은 /login으로
              보냈다가 로그인 후 이 화면으로 복귀시킨다(폼을 채우다 제출 단계에서 막히는 걸 방지). */}
          <Route element={<ProtectedRoute />}>
            <Route path="/fair-applications/new" element={<FairApplicationNewPage />} />
          </Route>
          {/* 내 신청 현황은 마이페이지 사이드바 안으로 옮겼다. 옛 경로는 호환용 리다이렉트. */}
          <Route path="/fair-applications/me" element={<Navigate to="/mypage/fair-applications" replace />} />
          {/* 내 신청 상세·수정과 알림은 본인 데이터만 보이는 화면이라 로그인 필수. */}
          <Route element={<ProtectedRoute />}>
            <Route path="/fair-applications/me/:fairId" element={<MyFairApplicationDetailPage />} />
            <Route path="/fair-applications/me/:fairId/edit" element={<FairApplicationEditPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
          </Route>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/mypage/password" element={<PasswordChangePage />} />
            <Route element={<ProtectedRoute roles={["SUPER_ADMIN"]} />}>
              <Route path="/account/settings" element={<AdminAccountSettingsPage />} />
            </Route>
            {/* dev의 새 사이드바형 마이페이지. 역할과 상관없이 로그인한 계정이면 모두 쓴다 -
                관리자도 개인 자격으로는 예약을 하는 관람객이라, 예약 내역·리뷰 같은 개인 활동
                화면이 필요하다. 관리자 전용 계정 화면(/account/settings)은 그대로 남아 있다. */}
            <Route path="/mypage" element={<MyPageLayout />}>
              <Route index element={<MyPageHome />} />
              <Route path="reservations" element={<MyReservationsPage />} />
              <Route path="booths/visited" element={<MyVisitedBoothsPage />} />
              <Route path="recommendation" element={<MyRecommendationPage />} />
              <Route path="favorites" element={<BoothFavoritesPage />} />
              <Route path="reviews" element={<MyReviewsPage />} />
              <Route path="fair-applications" element={<MyFairApplicationsPage />} />
              <Route path="businesses" element={<MyBusinessesPage />} />
              <Route path="pets" element={<MyPetsPage />} />
              <Route path="edit" element={<EditProfilePage />} />
              <Route path="account" element={<AccountSettingsPage />} />
            </Route>
            {/* 상세·입력 폼은 넓게 봐야 하는 화면이라 사이드바 밖에 둔다. */}
            <Route path="/mypage/pets/new" element={<PetFormPage />} />
            <Route path="/mypage/pets/:petId" element={<PetDetailPage />} />
            <Route path="/booths/:boothId/edit" element={<BoothEditPage />} />
            <Route path="/booths/:boothId/stats" element={<BoothStatsPage />} />
            {/* 옛 경로는 마이페이지 안의 새 위치로 리다이렉트(북마크·내부 링크 호환). */}
            <Route path="/booths/favorites/me" element={<Navigate to="/mypage/favorites" replace />} />
            <Route path="/booths/visited/me" element={<Navigate to="/mypage/booths/visited" replace />} />
            {/* 부스 콘솔로 이관: 옛 경로는 콘솔로 리다이렉트(북마크·내부 링크 호환). */}
            <Route path="/booths/me" element={<Navigate to="/vendor/booths" replace />} />
          </Route>
          <Route path="/reservations/me" element={<Navigate to="/mypage/reservations" replace />} />
          {/* 예약 상세는 내 예약만 보이는 화면이라 로그인 필수(QR·환불 정보가 들어 있다). */}
          <Route element={<ProtectedRoute />}>
            <Route path="/reservations/me/:reservationId" element={<ReservationDetailPage />} />
          </Route>
          {/* 행사 목록은 예정·진행·종료를 상태 배지로 구분하는 통합 목록 하나뿐이다.
              옛 "지난 행사" 경로(북마크·외부 링크)로 들어와도 같은 목록으로 넘긴다. */}
          <Route path="/fairs/upcoming" element={<FairListPage />} />
          <Route path="/fairs/past" element={<Navigate to="/fairs/upcoming" replace />} />
          {/* 행사 상세(공개). 목록 카드가 여기로 오고, '예약하기'는 /tickets/:fairId로 넘긴다.
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
          {/* 예약은 로그인 필수. 미로그인으로 화면을 열면 날짜·유형을 다 고르고 마지막
              예약 API에서 401로 막히므로, 들어오는 순간 /login으로 보냈다가 복귀시킨다. */}
          <Route element={<ProtectedRoute />}>
            <Route path="/tickets/:fairId" element={<TicketReservationPage />} />
          </Route>
          {/* 토스 결제창이 돌아오는 착지 경로. src/payments/toss.ts의 successUrl·failUrl과 일치해야 한다. */}
          <Route path="/payments/success" element={<PaymentSuccessPage />} />
          <Route path="/payments/fail" element={<PaymentFailPage />} />
          {/* 개설비 결제는 fair-admin 콘솔(사이드바 포함) 아래로 옮겼다(2026-08-24, 메뉴에서
              눌러 들어가면 사이드바가 사라지던 문제). 옛 경로는 이미 발송된 알림·이메일 링크가
              깨지지 않도록 리다이렉트만 남긴다. */}
          <Route path="/payments/fair-opening-fee" element={<Navigate to="/fair-admin/payments/fair-opening-fee" replace />} />
          <Route
            path="/payments/fair-opening-fee/:fairId"
            element={<FairOpeningFeeLegacyRedirect />}
          />
          <Route path="/booths/scan" element={<Navigate to="/vendor/scan" replace />} />
          <Route path="/businesses" element={<BusinessesByFairPage />} />
          <Route path="/fairs/:fairId/booths" element={<FairBoothsPage />} />
          {/* 부스 추천 + 동선 추천(같은 입력으로 API 2개를 호출해 탭으로 결과를 나눠 보여준다). */}
          <Route path="/fairs/:fairId/booth-recommendations" element={<BoothRecommendationPage />} />
          {/* 태그 기반 통합 리뷰(V39) 작성 마법사. 로그인 필요. */}
          <Route element={<ProtectedRoute />}>
            <Route path="/fairs/:fairId/reviews/new" element={<FairReviewWizardPage />} />
          </Route>
          {/* 사업자 등록은 내 계정에 사업자를 붙이는 작업이라 로그인 필수. */}
          <Route element={<ProtectedRoute />}>
            <Route path="/businesses/new" element={<BusinessRegisterPage />} />
          </Route>
          <Route path="/businesses/me" element={<Navigate to="/mypage/businesses" replace />} />
          <Route path="/businesses/:businessId" element={<BusinessDetailPage />} />
          <Route path="/participations/new" element={<ParticipationNewPage />} />
          <Route path="/fairs/:fairId/recruit-notice" element={<RecruitNoticeDetailPage />} />
          {/* 참가 신청서도 로그인 필수. 미로그인은 /login으로 보냈다가 복귀. 사업자 미등록은
              페이지 안에서 /businesses/new로 보내고(returnTo 포함) 등록 후 다시 이 화면으로 돌아온다. */}
          <Route element={<ProtectedRoute />}>
            <Route path="/fairs/:fairId/apply" element={<ApplicationSubmitPage />} />
          </Route>
          <Route path="/participations/me" element={<Navigate to="/vendor/participations" replace />} />
          {/* 내 참가 신청 상세·수정도 본인 데이터 화면이라 로그인 필수. */}
          <Route element={<ProtectedRoute />}>
            <Route path="/participations/me/:applicationId" element={<ApplicationDetailPage />} />
            <Route path="/participations/me/:applicationId/edit" element={<ApplicationEditPage />} />
          </Route>
          <Route path="/booths/:boothId" element={<BoothDetailPage />} />
          {/* 푸터에서 들어오는 공개 안내 페이지. 로그인 없이 누구나 봐야 하는 내용이다. */}
          <Route path="/about" element={<AboutPage />} />
          <Route path="/terms" element={<TermsPage />} />
          <Route path="/privacy" element={<PrivacyPage />} />
          <Route path="/contact" element={<ContactPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
        {/* 행사 관리자 콘솔 - EVENT_ADMIN(+ 상위 SUPER_ADMIN) 전용. URL 직접 진입도 role로 가드한다. */}
        <Route element={<ProtectedRoute roles={["EVENT_ADMIN", "SUPER_ADMIN"]} />}>
          <Route path="fair-admin" element={<FairAdminLayout />}>
            <Route
              index
              element={
                <ConsoleHome
                  consoleLabel="행사 관리자"
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
            {/* 예약자별 상세 목록은 통계 화면(위)에 합쳤다 - 옛 링크가 죽지 않도록 리다이렉트만 남긴다. */}
            <Route path="reservations/list" element={<Navigate to="/fair-admin/reservations" replace />} />
            <Route path="payments" element={<FairPaymentSettlementPage />} />
            {/* 참가비 결제 목록의 "상세" 링크 전용(2026-08-24) - 최고관리자 결제상세(admin/payments)와
                같은 PaymentDetailPage를 그대로 재사용한다. */}
            <Route path="payment-detail" element={<PaymentDetailPage />} />
            <Route path="payments/fair-opening-fee" element={<FairOpeningFeeSelectPage />} />
            <Route path="payments/fair-opening-fee/:fairId" element={<FairOpeningFeePaymentPage />} />
            {/* 리뷰 통계는 방문 통계와 합쳐졌다 - 옛 북마크/링크가 죽지 않도록 리다이렉트만 남긴다. */}
            <Route path="reviews" element={<Navigate to="/fair-admin/statistics" replace />} />
            <Route path="reviews/manage" element={<ReviewDeletionPage />} />
            <Route path="statistics" element={<FairStatsPage />} />
            <Route path="statistics/booths/:fairId" element={<BoothVisitStatsPage />} />
            <Route path="cancellation" element={<FairCancelRequestPage />} />
            <Route path="recruit-notice" element={<RecruitNoticeFormPage />} />
            <Route path="recruit-notice/:fairId" element={<RecruitNoticeFormPage />} />
            <Route path="participations" element={<ParticipationReviewPage />} />
            <Route path="cancellation-requests" element={<CancelRequestReviewPage />} />
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
            <Route path="dashboard/fairs/:fairId/reservations" element={<AdminFairReservationStatusPage />} />
            <Route path="fair-applications" element={<FairApplicationReviewPage />} />
            <Route path="audit-logs" element={<AuditLogPage />} />
            <Route path="payments" element={<PaymentDetailPage />} />
            <Route path="payments/list" element={<PaymentListPage />} />
            {/* 환불 전용 조회 화면(/admin/refunds)은 없앴다(2026-08-23) - 환불 정보가 결제 목록의
                상태 배지와 결제 상세의 "환불 정보" 섹션으로 흡수돼 볼 곳이 두 군데가 됐고,
                사이드바 메뉴도 링크도 없어 주소를 직접 쳐야만 열리는 화면이었다. */}
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
            <Route path="*" element={<AdminFallback kind="super" />} />
          </Route>
        </Route>
        {/* 부스(참가업체) 콘솔 - VENDOR 전용. 흩어져 있던 참가업체 기능을 한 콘솔로 모은다. */}
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
