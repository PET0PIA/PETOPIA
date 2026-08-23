import { useNavigate, useParams } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { FairReviewWizard } from "../../components/review/FairReviewWizard";

/** /fairs/:fairId/reviews/new - 태그 기반 통합 리뷰 작성 마법사(로그인 필요, ProtectedRoute). */
export function FairReviewWizardPage() {
  const { fairId } = useParams();
  const navigate = useNavigate();
  const numericFairId = Number(fairId);

  if (!Number.isInteger(numericFairId) || numericFairId <= 0) {
    return (
      <PageContainer className="py-10">
        <PageHeader eyebrow="리뷰 작성" title="잘못된 접근이에요" description="행사 정보를 확인할 수 없어요." />
      </PageContainer>
    );
  }

  return (
    <PageContainer className="max-w-2xl py-10">
      <PageHeader eyebrow="리뷰 작성" title="방문 후기를 남겨주세요" description="선택한 항목은 담당자 통계와 다른 관람객들에게 참고 자료로 쓰여요." />
      <FairReviewWizard fairId={numericFairId} onComplete={() => navigate(`/fairs/${numericFairId}`)} />
    </PageContainer>
  );
}
