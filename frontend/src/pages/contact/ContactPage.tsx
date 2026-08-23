import { CalendarDays, CreditCard, KeyRound, Mail, Megaphone, MessageSquareText, ShieldCheck, Store } from "lucide-react";
import { Link } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Card } from "../../components/ui/Card";

/**
 * 문의(/contact). 문의 전용 백엔드가 없으므로 새 폼을 만들지 않고,
 * 이미 동작하는 창구(전역 상담 위젯 ChatWidget, 메일, 각 신청 화면)로 안내한다.
 *
 * 상담 위젯은 화면 오른쪽 아래에 항상 떠 있다(PublicLayout). 이 페이지에서 코드로 열려면
 * 위젯의 열림 상태를 바깥으로 빼야 해서, 지금은 위치만 안내한다.
 */

// 대표 문의처. 실제 운영 이메일이 정해지면 이 값만 바꾸면 된다(백엔드 없이 mailto로만 동작).
const CONTACT_EMAIL = "help@petopia.co.kr";

// 메일 앱을 열 때 미리 채워둘 제목·본문. 사용자가 빈칸만 채우면 되도록 항목을 준다.
const mailSubject = "[PETOPIA] 문의";
const mailBody = [
  "안녕하세요, PETOPIA에 문의드립니다.",
  "",
  "- 문의 유형(예약 / 결제·환불 / 계정 / 행사 참가 / 기타):",
  "- 관련 행사명:",
  "- 예약번호(있으면):",
  "- 연락 받을 이름·연락처:",
  "- 문의 내용:",
].join("\n");
const mailHref = `mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent(mailSubject)}&body=${encodeURIComponent(mailBody)}`;

// 문의보다 화면에서 바로 처리하는 게 빠른 용건들. 위쪽 항목이 문의량이 많은 순서다.
const selfServeLinks = [
  { icon: CreditCard, label: "예약 확인·취소·환불", desc: "내 예약에서 상태를 확인하고 취소를 신청할 수 있어요.", to: "/mypage/reservations" },
  { icon: KeyRound, label: "계정·비밀번호", desc: "이메일·비밀번호 변경과 로그인 관련 설정이 모여 있어요.", to: "/mypage/account" },
  { icon: CalendarDays, label: "행사 개최 신청", desc: "행사를 열고 싶다면 개최 신청서를 접수해 주세요.", to: "/fair-applications/new" },
  { icon: Store, label: "부스 참가 신청", desc: "참가업체로 부스를 열려면 모집 중인 행사에 신청하세요.", to: "/participations/new" },
  { icon: Megaphone, label: "광고 문의", desc: "홈 배너·팝업 광고는 광고 문의 창구로 안내해요.", to: "/advertising" },
  { icon: ShieldCheck, label: "개인정보 열람·삭제", desc: "처리방침에서 수집 항목과 요청 방법을 확인할 수 있어요.", to: "/privacy" },
] as const;

// 문의 메일·상담에 담으면 좋은 항목. 위 mailBody 템플릿과 같은 순서로 맞춰 둔다.
const inquiryChecklist = ["문의 유형", "관련 행사명", "예약번호 (있으면)", "연락 받을 이름 · 연락처"];

export function ContactPage() {
  return (
    <PageContainer className="py-7 sm:py-10">
      <PageHeader
        eyebrow="PETOPIA 문의"
        title="문의하기"
        description="용건에 맞는 창구로 안내해 드려요. 예약·결제처럼 직접 확인할 수 있는 내용은 아래 바로가기가 더 빠릅니다."
      />

      {/* 1. 바로 문의할 수 있는 두 창구 */}
      <div className="grid gap-3 sm:grid-cols-2">
        <Card className="flex h-full flex-col p-6">
          <div className="mb-3 grid size-11 place-items-center rounded-card bg-primary-soft text-primary-strong">
            <MessageSquareText size={22} aria-hidden="true" />
          </div>
          <h2 className="text-base font-extrabold text-ink">실시간 상담</h2>
          <p className="mt-1.5 flex-1 text-sm leading-6 text-muted">
            화면 오른쪽 아래의 말풍선 버튼을 누르면 상담 창이 열려요. 문의 유형을 고르면 담당자가 순서대로 답변해 드립니다.
            운영 시간이 지난 뒤 남긴 문의는 다음 영업일에 확인해요.
          </p>
          <p className="mt-4 text-xs font-bold text-muted">로그인 후 이용하면 지난 상담 내용이 이어집니다.</p>
        </Card>

        <Card className="flex h-full flex-col p-6">
          <div className="mb-3 grid size-11 place-items-center rounded-card bg-primary-soft text-primary-strong">
            <Mail size={22} aria-hidden="true" />
          </div>
          <h2 className="text-base font-extrabold text-ink">이메일 문의</h2>
          <p className="mt-1.5 flex-1 text-sm leading-6 text-muted">
            증빙 자료를 첨부해야 하거나 내용이 긴 문의는 메일이 편해요. 아래 버튼을 누르면 문의 양식이 채워진 메일 창이 열립니다.
          </p>
          <p className="mt-4 text-xs font-bold text-muted">문의 이메일</p>
          <p className="mt-0.5 text-sm font-bold text-ink">{CONTACT_EMAIL}</p>
          <a
            href={mailHref}
            className="mt-4 inline-flex min-h-11 items-center justify-center gap-2 rounded-button bg-primary-strong px-5 text-sm font-bold text-white transition hover:opacity-90"
          >
            <Mail size={16} aria-hidden="true" />
            메일로 문의하기
          </a>
        </Card>
      </div>

      {/* 2. 용건별 바로가기 */}
      <section className="mt-12">
        <h2 className="mb-4 text-xl font-extrabold text-ink">용건별 바로가기</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {selfServeLinks.map(({ icon: Icon, label, desc, to }) => (
            <Link key={label} to={to} className="group rounded-card border border-line bg-card p-5 transition hover:border-ink/40">
              <div className="flex items-start gap-3">
                <span className="grid size-9 shrink-0 place-items-center rounded-button bg-page text-ink"><Icon size={18} aria-hidden="true" /></span>
                <div className="min-w-0">
                  <h3 className="font-extrabold text-ink group-hover:text-primary-strong">{label}</h3>
                  <p className="mt-1 text-sm leading-6 text-muted">{desc}</p>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* 3. 문의 전에 챙기면 답변이 빨라지는 것들 */}
      <Card className="mt-12 p-6 sm:p-8">
        <h2 className="text-base font-extrabold text-ink">문의할 때 함께 알려주세요</h2>
        <p className="mt-1.5 text-sm leading-6 text-muted">
          아래 내용을 적어주시면 확인 절차 없이 바로 답변드릴 수 있어요. 답변은 영업일 기준 1~2일 안에 드립니다.
        </p>
        <ul className="mt-4 grid gap-1.5 text-sm text-ink sm:grid-cols-2">
          {inquiryChecklist.map((item) => (
            <li key={item} className="flex items-center gap-2">
              <span className="size-1.5 shrink-0 rounded-full bg-primary-strong" aria-hidden="true" />
              {item}
            </li>
          ))}
        </ul>
        <div className="mt-6 flex flex-wrap items-center gap-x-6 gap-y-2 border-t border-line pt-6">
          <Link to="/terms" className="text-sm font-bold text-primary-strong underline underline-offset-4 hover:opacity-80">이용약관</Link>
          <Link to="/privacy" className="text-sm font-bold text-primary-strong underline underline-offset-4 hover:opacity-80">개인정보 처리방침</Link>
          <Link to="/about" className="text-sm font-bold text-primary-strong underline underline-offset-4 hover:opacity-80">서비스 소개</Link>
        </div>
      </Card>

      <p className="mt-6 text-xs leading-5 text-muted">
        PETOPIA는 교육 과정에서 만든 실습 프로젝트입니다. 위 이메일은 예시 주소이며, 실제 고객 응대 창구가 아닙니다.
      </p>
    </PageContainer>
  );
}
