import { ArrowUpRight, CalendarDays, Heart, Megaphone, MessageSquareText, Newspaper, QrCode, Store, Ticket, UsersRound } from "lucide-react";
import { Link } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Card } from "../../components/ui/Card";

/**
 * 서비스 소개(/about). 푸터에서 들어오는 정적 안내 페이지라 API 호출이 없다.
 *
 * 링크는 실제로 있는 라우트만 건다 - 소개 페이지에서 막힌 링크를 만나면 서비스가 미완성으로 읽힌다.
 * 역할별 창구는 헤더 "비즈니스" 메뉴(config/navigation.ts)와 같은 경로를 써서 안내가 갈라지지 않게 한다.
 */

const features = [
  { icon: CalendarDays, title: "행사 일정 한눈에", desc: "전국에서 열리는 반려동물 행사 일정과 장소를 모아 봅니다.", path: "/fairs/upcoming" },
  { icon: Ticket, title: "사전 예약과 결제", desc: "현장에서 줄 서지 않도록 미리 티켓을 예약하고 결제할 수 있어요.", path: "/fairs/upcoming" },
  { icon: Store, title: "참가 부스 미리보기", desc: "어떤 브랜드가 나오는지 먼저 살펴보고 관심 부스를 찜해 둡니다.", path: "/businesses" },
  { icon: Newspaper, title: "소식과 이벤트", desc: "공지와 진행 중인 이벤트를 확인하고, 다녀온 행사의 후기도 남겨요.", path: "/news" },
] as const;

const steps = [
  { icon: CalendarDays, title: "행사 고르기", desc: "일정·지역·주제를 보고 갈 만한 행사를 정해요." },
  { icon: Ticket, title: "예약·결제", desc: "인원과 반려동물 정보를 넣고 티켓을 예약해요." },
  { icon: QrCode, title: "현장 입장", desc: "예약에 담긴 QR로 입구에서 바로 입장해요." },
  { icon: Heart, title: "후기 남기기", desc: "다녀온 부스에 대한 후기를 남겨 다음 관람객에게 알려줘요." },
] as const;

const audiences = [
  { icon: UsersRound, label: "관람객", desc: "가고 싶은 행사를 찾아 예약하고, 다녀온 부스와 후기를 모아 봅니다.", to: "/fairs/upcoming", cta: "행사 둘러보기" },
  { icon: CalendarDays, label: "행사 관리자", desc: "행사 개최를 신청하고, 승인 후 예약·부스·현장 운영을 관리합니다.", to: "/fair-applications/new", cta: "개최 신청하기" },
  { icon: Store, label: "참가업체", desc: "부스 참가를 신청하고, 우리 브랜드와 상품을 관람객에게 소개합니다.", to: "/participations/new", cta: "부스 참가 신청" },
  { icon: Megaphone, label: "광고주", desc: "홈 배너·팝업으로 반려동물 가족이 모인 곳에 브랜드를 알립니다.", to: "/advertising", cta: "광고 문의하기" },
] as const;

export function AboutPage() {
  return (
    <PageContainer className="space-y-14 py-7 sm:py-10">
      <PageHeader
        eyebrow="PETOPIA 소개"
        title="반려동물과 사람이 함께 행복한 순간을 만듭니다."
        description="PETOPIA는 전국의 반려동물 행사를 찾고, 예약하고, 다녀온 기록까지 남기는 서비스예요. 행사를 여는 주최측과 부스로 참여하는 참가업체도 같은 곳에서 신청하고 운영합니다."
      />

      {/* 1. 무엇을 할 수 있나 */}
      <section>
        <h2 className="mb-4 text-xl font-extrabold text-ink">PETOPIA에서 할 수 있는 일</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {features.map(({ icon: Icon, title, desc, path }) => (
            <Link key={title} to={path} className="group relative rounded-card border border-line bg-card p-5 transition hover:border-ink/40">
              <span className="mb-5 grid size-11 place-items-center rounded-button bg-page text-ink"><Icon size={21} aria-hidden="true" /></span>
              <h3 className="font-extrabold text-ink">{title}</h3>
              <p className="mt-1.5 text-sm leading-6 text-muted">{desc}</p>
              <ArrowUpRight className="absolute right-5 top-5 text-muted transition group-hover:text-ink" size={18} aria-hidden="true" />
            </Link>
          ))}
        </div>
      </section>

      {/* 2. 이용 흐름 */}
      <section>
        <h2 className="mb-4 text-xl font-extrabold text-ink">이렇게 이용해요</h2>
        <ol className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {steps.map(({ icon: Icon, title, desc }, index) => (
            <li key={title}>
              <Card className="h-full p-5">
                <div className="mb-4 flex items-center gap-2">
                  <span className="grid size-7 place-items-center rounded-full bg-primary-soft text-xs font-extrabold text-primary-strong">{index + 1}</span>
                  <Icon size={18} className="text-muted" aria-hidden="true" />
                </div>
                <h3 className="font-extrabold text-ink">{title}</h3>
                <p className="mt-1.5 text-sm leading-6 text-muted">{desc}</p>
              </Card>
            </li>
          ))}
        </ol>
      </section>

      {/* 3. 역할별 창구 - 헤더 '비즈니스' 메뉴와 같은 경로로 연결한다. */}
      <section>
        <h2 className="mb-4 text-xl font-extrabold text-ink">이런 분들이 쓰고 있어요</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {audiences.map(({ icon: Icon, label, desc, to, cta }) => (
            <Card key={label} className="flex h-full flex-col p-6">
              <div className="mb-3 grid size-11 place-items-center rounded-card bg-primary-soft text-primary-strong"><Icon size={22} aria-hidden="true" /></div>
              <h3 className="font-extrabold text-ink">{label}</h3>
              <p className="mt-1.5 flex-1 text-sm leading-6 text-muted">{desc}</p>
              <Link to={to} className="mt-4 inline-flex items-center gap-1 text-sm font-bold text-primary-strong underline underline-offset-4 hover:opacity-80">
                {cta}
                <ArrowUpRight size={15} aria-hidden="true" />
              </Link>
            </Card>
          ))}
        </div>
      </section>

      {/* 4. 마무리 */}
      <section className="surface flex flex-col gap-4 p-6 sm:flex-row sm:items-center sm:justify-between sm:p-8">
        <div>
          <h2 className="text-lg font-extrabold text-ink">가까운 행사부터 확인해 보세요.</h2>
          <p className="mt-1.5 text-sm leading-6 text-muted">궁금한 점이 있으면 문의 페이지에서 상담을 남기거나 메일로 알려주세요.</p>
        </div>
        <div className="flex flex-wrap gap-3">
          <Link to="/fairs/upcoming" className="inline-flex min-h-11 items-center justify-center gap-2 rounded-button bg-primary-strong px-4 text-sm font-bold text-white transition hover:opacity-90">
            행사 보러가기
          </Link>
          <Link to="/contact" className="inline-flex min-h-11 items-center justify-center gap-2 rounded-button border border-line bg-card px-4 text-sm font-bold text-ink transition hover:bg-page">
            <MessageSquareText size={16} aria-hidden="true" />
            문의하기
          </Link>
        </div>
      </section>

      <p className="text-xs leading-5 text-muted">
        PETOPIA는 교육 과정에서 만든 실습 프로젝트입니다. 화면에 보이는 행사·부스·후기 정보는 기능 확인을 위한 데이터이며, 실제 예약이나 결제 서비스를 제공하지 않습니다.
      </p>
    </PageContainer>
  );
}
