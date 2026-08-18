import { Images, Mail, MessageSquareText, Sparkles } from "lucide-react";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Card } from "../../components/ui/Card";

// 광고 문의처. 실제 광고 영업 이메일이 정해지면 이 값만 바꾸면 된다(백엔드 없이 mailto로만 동작).
const AD_INQUIRY_EMAIL = "ad@petopia.co.kr";

// 메일 앱을 열 때 미리 채워둘 제목·본문. 문의에 필요한 항목을 사용자가 빈칸만 채우도록 돕는다.
const mailSubject = "[PETOPIA] 광고 문의";
const mailBody = [
  "안녕하세요, PETOPIA 광고를 문의드립니다.",
  "",
  "- 브랜드/회사명:",
  "- 희망 광고 상품(홈 배너 / 팝업):",
  "- 희망 게재 기간:",
  "- 담당자 이름·연락처:",
  "- 문의 내용:",
].join("\n");
const mailHref = `mailto:${AD_INQUIRY_EMAIL}?subject=${encodeURIComponent(mailSubject)}&body=${encodeURIComponent(mailBody)}`;

// 광고 상품 소개. 백엔드의 배너(/api/banners)·팝업(/api/popups) 노출 영역과 짝을 이룬다.
const adProducts = [
  {
    icon: Images,
    name: "홈 메인 배너",
    desc: "홈 화면 상단에 크게 노출되는 배너예요. PETOPIA를 여는 모든 방문자에게 브랜드를 알릴 수 있어요.",
  },
  {
    icon: Sparkles,
    name: "팝업 광고",
    desc: "접속 시 화면 가운데에 뜨는 팝업이에요. 이벤트·신규 오픈처럼 확실히 알리고 싶은 소식에 좋아요.",
  },
];

// 문의 메일에 담으면 좋은 항목. 위 mailBody 템플릿과 같은 순서로 맞춰 둔다.
const inquiryChecklist = [
  "브랜드 / 회사명",
  "희망 광고 상품 (홈 배너 · 팝업)",
  "희망 게재 기간",
  "담당자 이름 · 연락처",
];

/** 광고 문의 안내(정적). 문의 전용 백엔드가 없어 이메일(mailto)로만 연결한다. */
export function AdvertisingInquiryPage() {
  return (
    <PageContainer className="py-7 sm:py-10">
      <PageHeader
        eyebrow="PETOPIA 광고"
        title="광고 문의"
        description="반려동물 가족이 모이는 PETOPIA에서 브랜드를 알려보세요. 아래 광고 상품을 확인하고 이메일로 문의해 주세요."
      />

      {/* 광고 상품 소개 */}
      <div className="grid gap-4 sm:grid-cols-2">
        {adProducts.map((product) => (
          <Card key={product.name} className="p-6">
            <div className="mb-3 grid size-11 place-items-center rounded-card bg-primary-soft text-primary-strong">
              <product.icon size={22} aria-hidden="true" />
            </div>
            <h2 className="text-base font-extrabold text-ink">{product.name}</h2>
            <p className="mt-1.5 text-sm leading-6 text-muted">{product.desc}</p>
          </Card>
        ))}
      </div>

      {/* 문의 방법 */}
      <Card className="mt-4 p-6 sm:p-8">
        <div className="mb-4 flex items-center gap-2 text-sm font-bold text-ink">
          <MessageSquareText size={18} aria-hidden="true" />
          문의 방법
        </div>

        <p className="text-sm leading-6 text-muted">
          아래 버튼을 누르면 문의 양식이 채워진 메일 창이 열려요. 빈칸을 채워 보내주시면
          담당자가 영업일 기준 2~3일 안에 회신드려요.
        </p>

        <div className="mt-4">
          <p className="mb-2 text-xs font-bold text-muted">문의에 담아주시면 좋은 내용</p>
          <ul className="grid gap-1.5 text-sm text-ink sm:grid-cols-2">
            {inquiryChecklist.map((item) => (
              <li key={item} className="flex items-center gap-2">
                <span className="size-1.5 shrink-0 rounded-full bg-primary-strong" aria-hidden="true" />
                {item}
              </li>
            ))}
          </ul>
        </div>

        <div className="mt-6 flex flex-col gap-3 border-t border-line pt-6 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-xs font-bold text-muted">문의 이메일</p>
            <p className="mt-0.5 text-sm font-bold text-ink">{AD_INQUIRY_EMAIL}</p>
          </div>
          <a
            href={mailHref}
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-button bg-primary-strong px-5 text-sm font-bold text-white transition hover:opacity-90"
          >
            <Mail size={16} aria-hidden="true" />
            메일로 문의하기
          </a>
        </div>
      </Card>
    </PageContainer>
  );
}
