import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";

/**
 * 마이페이지 - 내 리뷰. 별점+텍스트 리뷰(수정·삭제 가능)는 V39에서 태그 기반 통합
 * 리뷰로 교체되며 폐기됐다. 새 리뷰는 수정·삭제 API가 없어 "새로 작성"만 가능하고,
 * 행사별 작성 여부는 GET /api/fairs/{fairId}/reviews/status로만 확인할 수 있어
 * 아직 전체 목록 화면은 없다(petopia-review-feature-plan 스킬 참고).
 */
export function MyReviewsPage() {
  return (
    <PageContainer className="py-10">
      <PageHeader eyebrow="마이페이지" title="내 리뷰" description="내가 남긴 행사 리뷰 목록 화면을 준비하고 있어요." />
      <EmptyState
        title="준비 중인 화면이에요."
        description="각 행사 상세 페이지에서 리뷰 작성 여부를 확인할 수 있어요."
        actionTo="/fairs/upcoming"
        actionLabel="행사 목록 보기"
      />
    </PageContainer>
  );
}
