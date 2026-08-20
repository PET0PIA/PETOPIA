import { AlertCircle, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { useConfirm } from "../../components/ui/useConfirm";
import { useFairSelector } from "../../contexts/FairSelectorContext";
import { ApiError } from "../../api/client";
import { deleteFairReview, getFairReviewList, type FairReviewListItem } from "../../api/fairReviewList";
import type { CompanionType, VisitPurpose } from "../../api/fairReviewSubmission";
import { formatShortDate } from "../../utils/date";

const PAGE_SIZE = 20;

const companionTypeLabels: Record<CompanionType, string> = {
  ALONE: "혼자",
  WITH_PET: "반려동물과 함께",
  WITH_FAMILY: "가족과 함께",
  WITH_FRIEND: "친구와 함께",
};

const visitPurposeLabels: Record<VisitPurpose, string> = {
  SHOPPING: "쇼핑",
  EXPERIENCE: "체험",
  INFO: "정보 습득",
  ETC: "기타",
};

/**
 * 박람회 관리자 콘솔 - 리뷰 관리(삭제 전용). 리뷰 통계(ReviewManagementPage, "/fair-admin/reviews")와
 * 역할을 분리했다 - 통계 화면은 집계만 보여주고, 여기서는 부적절한 리뷰 1건을 골라 삭제한다.
 * 목록 조회는 공개 목록 API(GET /reviews)를 그대로 재사용한다 - 닉네임·태그·작성일까지
 * 이미 다 내려주고 있어 관리자 전용 조회 API를 새로 만들지 않았다(petopia-review-feature-plan
 * 스킬 참고). 삭제는 하드 삭제라 되돌릴 수 없어 useConfirm으로 한 번 더 확인받는다.
 */
export function ReviewDeletionPage() {
  const { fairId } = useFairSelector();
  const { confirm, confirmDialog } = useConfirm();

  const [items, setItems] = useState<FairReviewListItem[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    if (fairId === null) return;
    let ignore = false;

    setLoading(true);
    setLoadError(null);
    getFairReviewList(fairId, 0, PAGE_SIZE)
      .then((data) => {
        if (ignore) return;
        setItems(data.items);
        setHasNext(data.hasNext);
        setTotalElements(data.totalElements);
        setPage(0);
      })
      .catch((error) => {
        if (!ignore) {
          setItems([]);
          setLoadError(error instanceof ApiError ? error.message : "리뷰 목록을 불러오지 못했어요.");
        }
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [fairId]);

  function loadMore() {
    if (fairId === null) return;
    const nextPage = page + 1;
    setLoadingMore(true);
    getFairReviewList(fairId, nextPage, PAGE_SIZE)
      .then((data) => {
        setItems((prev) => [...prev, ...data.items]);
        setHasNext(data.hasNext);
        setPage(nextPage);
      })
      .catch((error) => {
        setActionError(error instanceof ApiError ? error.message : "리뷰 목록을 더 불러오지 못했어요.");
      })
      .finally(() => setLoadingMore(false));
  }

  async function handleDelete(review: FairReviewListItem) {
    if (fairId === null) return;
    const confirmed = await confirm({
      title: "리뷰를 삭제할까요?",
      description: `'${review.nickname}'님의 리뷰를 삭제할까요? 딸린 부스 평가까지 함께 지워지고, 되돌릴 수 없어요.`,
      confirmLabel: "삭제",
    });
    if (!confirmed) return;

    setActionError(null);
    setBusyId(review.reviewId);
    try {
      await deleteFairReview(fairId, review.reviewId);
      setItems((prev) => prev.filter((item) => item.reviewId !== review.reviewId));
      setTotalElements((prev) => Math.max(0, prev - 1));
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : "리뷰를 삭제하지 못했어요.");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="mx-auto max-w-4xl py-2">
      <PageHeader
        eyebrow="박람회 관리자"
        title="리뷰 관리"
        description="부적절한 리뷰를 확인하고 삭제할 수 있어요. 집계된 통계는 '리뷰 통계' 메뉴에서 볼 수 있어요."
      />

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}
      {actionError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{actionError}</p>
        </div>
      )}

      {fairId === null && (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 리뷰 목록이 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
      )}

      {fairId !== null && loading && (
        <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>
      )}

      {fairId !== null && !loading && !loadError && items.length === 0 && (
        <EmptyState title="아직 등록된 리뷰가 없어요." description="방문객이 리뷰를 남기면 이곳에서 확인하고 관리할 수 있어요." />
      )}

      {fairId !== null && !loading && !loadError && items.length > 0 && (
        <>
          <p className="mb-3 text-sm text-muted">전체 {totalElements}건</p>
          <div className="space-y-2">
            {items.map((review) => (
              <div key={review.reviewId} className="surface flex items-start gap-3 p-4">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm font-bold text-ink">{review.nickname}</span>
                    <span className="text-xs text-muted">{formatShortDate(review.createdAt)}</span>
                  </div>
                  <p className="mt-1 text-xs text-muted">
                    {companionTypeLabels[review.companionType]} · {visitPurposeLabels[review.visitPurpose]} · {review.wouldRevisit ? "재방문 의향 있음" : "재방문 의향 없음"}
                  </p>
                  {review.fairTagLabels.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {review.fairTagLabels.map((label) => (
                        <span key={label} className="rounded-full bg-primary-soft px-2.5 py-1 text-xs font-bold text-primary-strong">
                          {label}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
                <button
                  type="button"
                  aria-label={`${review.nickname}님의 리뷰 삭제`}
                  disabled={busyId === review.reviewId}
                  className="shrink-0 rounded-button p-2 text-muted hover:bg-page hover:text-primary-strong disabled:cursor-not-allowed disabled:opacity-50"
                  onClick={() => handleDelete(review)}
                >
                  <Trash2 size={16} />
                </button>
              </div>
            ))}
          </div>

          {hasNext && (
            <button
              type="button"
              onClick={loadMore}
              disabled={loadingMore}
              className="mt-4 inline-flex min-h-11 w-full items-center justify-center rounded-button border border-line bg-card text-sm font-bold transition hover:bg-page disabled:opacity-50"
            >
              {loadingMore ? "불러오는 중…" : "더 보기"}
            </button>
          )}
        </>
      )}

      {confirmDialog}
    </div>
  );
}
