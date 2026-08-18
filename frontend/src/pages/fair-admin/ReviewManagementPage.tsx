import { useEffect, useState } from "react";
import { Star } from "lucide-react";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { AdminReviewReplyForm } from "../../components/review/AdminReviewReplyForm";
import { ApiError } from "../../api/client";
import { getFairReviews, getFairReviewSummary, type FairReviewListItem, type FairReviewSummary } from "../../api/review";
import { useFairSelector } from "../../contexts/FairSelectorContext";

const PAGE_SIZE = 10;

// "2026-08-14T10:30:00" → "2026.08.14"
function formatReviewDate(iso: string): string {
  return /^\d{4}-\d{2}-\d{2}/.test(iso) ? iso.slice(0, 10).replace(/-/g, ".") : iso;
}

function StarRating({ rating }: { rating: number }) {
  return (
    <span className="inline-flex gap-0.5" aria-label={`별점 ${rating}점`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Star key={n} size={14} fill={n <= rating ? "currentColor" : "none"} className={n <= rating ? "text-sun" : "text-muted"} aria-hidden="true" />
      ))}
    </span>
  );
}

/**
 * 박람회 관리자 콘솔 - 담당 행사 리뷰에 답글을 다는 화면. 목록·요약 조회는 공개 API를
 * 그대로 재사용한다(권한 제한이 필요 없는 데이터라 관리자 전용 API를 따로 안 만들었다).
 * 답글 작성/수정은 리뷰 도메인 독립 모듈(AdminReviewReplyForm)을 그대로 붙인다 - 그 폼이
 * 호출하는 POST/PATCH .../reply는 SecurityConfig에서 EVENT_ADMIN/SUPER_ADMIN + 담당
 * 행사 여부(FairAdminAccessGuard)를 서버가 확인한다.
 */
export function ReviewManagementPage() {
  const { fairId } = useFairSelector();

  const [summary, setSummary] = useState<FairReviewSummary | null>(null);
  const [items, setItems] = useState<FairReviewListItem[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  // FairAdminLayout이 fairId를 key로 Outlet을 remount하므로(FairScopedOutlet), 이
  // 컴포넌트는 fairId 하나당 한 번만 마운트된다 - 같은 마운트에서 fairId가 바뀌는 경우가
  // 없어 loading/loadError를 effect 안에서 다시 리셋할 필요가 없다(초기값으로 충분).
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (fairId === null) return;
    let ignore = false;
    Promise.all([getFairReviewSummary(fairId), getFairReviews(fairId, 0, PAGE_SIZE)])
      .then(([summaryRes, listRes]) => {
        if (ignore) return;
        setSummary(summaryRes);
        setItems(listRes.items);
        setHasNext(listRes.hasNext);
        setPage(0);
      })
      .catch((error) => {
        if (!ignore) setLoadError(error instanceof ApiError ? error.message : "리뷰를 불러오지 못했어요.");
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
    getFairReviews(fairId, nextPage, PAGE_SIZE)
      .then((res) => {
        setItems((prev) => [...prev, ...res.items]);
        setHasNext(res.hasNext);
        setPage(nextPage);
      })
      .catch(() => {
        /* 더 보기 실패는 조용히 무시 - 이미 보이는 목록은 유지 */
      })
      .finally(() => setLoadingMore(false));
  }

  return (
    <div className="mx-auto max-w-3xl py-2">
      <PageHeader eyebrow="박람회 관리자" title="리뷰 관리" description="담당 행사에 달린 리뷰를 확인하고 답글을 남겨요." />

      {fairId === null && (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 리뷰 목록이 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
      )}

      {loadError && (
        <div className="surface mb-6 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">{loadError}</div>
      )}

      {fairId !== null && loading && <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {fairId !== null && !loading && !loadError && summary && (
        <p className="mb-4 text-sm text-muted">
          전체 <span className="font-bold text-ink">{summary.reviewCount}</span>개
          {summary.averageRating != null && (
            <>
              {" "}
              · 평균 <span className="font-bold text-ink">{summary.averageRating.toFixed(1)}</span>
            </>
          )}
        </p>
      )}

      {fairId !== null && !loading && !loadError && items.length === 0 && (
        <EmptyState title="아직 등록된 리뷰가 없어요." description="관람객이 리뷰를 남기면 이곳에서 확인할 수 있어요." />
      )}

      {fairId !== null && items.length > 0 && (
        <div className="flex flex-col gap-4">
          {items.map((review) => (
            <div key={review.reviewId} className="surface p-4">
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-bold">{review.nickname}</span>
                <span className="text-xs text-muted">{formatReviewDate(review.createdAt)}</span>
              </div>
              <div className="mt-1">
                <StarRating rating={review.rating} />
              </div>
              <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-ink">{review.content}</p>

              <div className="mt-3 border-t border-line pt-3">
                <AdminReviewReplyForm fairId={fairId} reviewId={review.reviewId} />
              </div>
            </div>
          ))}

          {hasNext && (
            <Button type="button" variant="outline" onClick={loadMore} disabled={loadingMore} className="w-full">
              {loadingMore ? "불러오는 중…" : "리뷰 더 보기"}
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
