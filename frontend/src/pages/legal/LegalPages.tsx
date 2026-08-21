import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { Card } from "../../components/ui/Card";
import { LEGAL_EFFECTIVE_DATE, PrivacyContent, TermsContent } from "../../components/legal/LegalDocuments";

/**
 * 푸터에서 들어오는 정책 페이지(/terms, /privacy).
 *
 * 본문은 회원가입 동의 팝업과 같은 원본(components/legal/LegalDocuments)을 쓴다 - "동의한 내용"과
 * "공개된 내용"이 어긋나지 않게 하려는 것이므로, 여기에 문구를 직접 적지 않는다.
 */

const linkClass = "text-sm font-bold text-primary-strong underline underline-offset-4 hover:opacity-80";

function LegalDocumentPage({ title, children, otherDoc }: { title: string; children: ReactNode; otherDoc: { to: string; label: string } }) {
  return (
    <PageContainer className="py-7 sm:py-10">
      <PageHeader
        eyebrow="PETOPIA 정책"
        title={title}
        description={`시행 예정일 ${LEGAL_EFFECTIVE_DATE} · 회원가입 화면에서 동의하는 내용과 같은 문서예요.`}
      />
      <Card className="p-6 sm:p-8">{children}</Card>
      {/* 정책 페이지는 서로 오가며 확인하는 경우가 많아 같은 자리에 이동 링크를 둔다. */}
      <div className="mt-6 flex flex-wrap items-center gap-x-6 gap-y-2">
        <Link to={otherDoc.to} className={linkClass}>{otherDoc.label}</Link>
        <Link to="/contact" className={linkClass}>문의하기</Link>
      </div>
      <p className="mt-6 text-xs leading-5 text-muted">
        본 문서는 법무 검토 전 초안입니다. PETOPIA는 교육 과정 실습으로 만든 서비스이며, 실제 운영 주체가 정해지면 사업자 정보와 정책을 다시 확정해 게시합니다.
      </p>
    </PageContainer>
  );
}

export function TermsPage() {
  return (
    <LegalDocumentPage title="이용약관" otherDoc={{ to: "/privacy", label: "개인정보 처리방침 보기" }}>
      <TermsContent />
    </LegalDocumentPage>
  );
}

export function PrivacyPage() {
  return (
    <LegalDocumentPage title="개인정보 처리방침" otherDoc={{ to: "/terms", label: "이용약관 보기" }}>
      <PrivacyContent />
    </LegalDocumentPage>
  );
}
