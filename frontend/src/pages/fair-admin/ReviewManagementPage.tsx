import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { useFairSelector } from "../../contexts/FairSelectorContext";

/**
 * 박람회 관리자 콘솔 - 리뷰 관리. 별점+텍스트 리뷰(답글 기능 포함)는 V39에서 태그 기반
 * 통합 리뷰로 교체되며 폐기됐다. 관리자용 화면은 행사관리자 통계 페이지(Phase 6,
 * GET /api/fairs/{fairId}/reviews/stats)에서 예약/입장 통계와 함께 제공할 예정이라
 * 그때까지는 안내만 띄운다(petopia-review-feature-plan 스킬 참고).
 */
export function ReviewManagementPage() {
  const { fairId } = useFairSelector();

  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader eyebrow="박람회 관리자" title="리뷰 관리" description="담당 행사에 달린 리뷰 통계를 확인해요." />
      {fairId === null ? (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 리뷰 통계가 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
      ) : (
        <EmptyState title="준비 중인 화면이에요." description="태그 기반으로 다시 만든 리뷰 통계 화면을 곧 이 자리에서 볼 수 있어요." />
      )}
    </div>
  );
}
