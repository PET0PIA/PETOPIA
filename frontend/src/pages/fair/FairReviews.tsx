import { useEffect, useState } from "react";
import { Star } from "lucide-react";
import { getFairReviewSummary, getFairReviews, type FairReviewListItem, type FairReviewSummary } from "../../api/review";

const PAGE_SIZE = 10;

// "2026-08-14T10:30:00" → "2026.08.14"
function formatReviewDate(iso: string): string {
  return /^\d{4}-\d{2}-\d{2}/.test(iso) ? iso.slice(0, 10).replace(/-/g, ".") : iso;
}

/** 별 5개 중 rating만큼 채운다. */
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
 * 행사 상세의 "리뷰" 섹션. 요약(평균 평점·개수)과 목록을 공개 API로 가져온다(비로그인 조회 가능).
 * verifiedVisit은 백엔드가 아직 항상 false라 화면에 쓰지 않는다. 리뷰가 0개면 섹션을 숨긴다(작성 진입은 후속).
 */
export function FairReviews({ fairId }: { fairId: number }) {
  const [summary, setSummary] = useState<FairReviewSummary | null>(null);
  const [items, setItems] = useState<FairReviewListItem[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    let alive = true;
    Promise.allSettled([getFairReviewSummary(fairId), getFairReviews(fairId, 0, PAGE_SIZE)]).then(([summaryRes, listRes]) => {
      if (!alive) return;
      if (summaryRes.status === "fulfilled") setSummary(summaryRes.value);
      if (listRes.status === "fulfilled") {
        setItems(listRes.value.items);
        setHasNext(listRes.value.hasNext);
      }
      setLoading(false);
    });
    return () => {
      alive = false;
    };
  }, [fairId]);

  function loadMore() {
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

  const count = summary?.reviewCount ?? 0;
  // 로딩 중이거나 리뷰가 없으면 섹션을 숨긴다.
  if (loading || count === 0) return null;

  return (
    <section className="mt-10">
      <div className="flex items-baseline gap-3">
        <h2 className="text-lg font-extrabold">
          리뷰 <span className="text-sm font-normal text-muted">{count}</span>
        </h2>
        {summary?.averageRating != null && (
          <span className="flex items-center gap-1 text-sm font-bold">
            <Star size={16} fill="currentColor" className="text-sun" aria-hidden="true" />
            {summary.averageRating.toFixed(1)}
          </span>
        )}
      </div>

      <ul className="mt-4 flex flex-col gap-4">
        {items.map((review) => (
          <li key={review.reviewId} className="border-b border-line pb-4 last:border-0">
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm font-bold">{review.nickname}</span>
              <span className="text-xs text-muted">{formatReviewDate(review.createdAt)}</span>
            </div>
            <div className="mt-1">
              <StarRating rating={review.rating} />
            </div>
            <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-ink">{review.content}</p>
          </li>
        ))}
      </ul>

      {hasNext && (
        <button
          type="button"
          onClick={loadMore}
          disabled={loadingMore}
          className="mt-4 inline-flex min-h-11 w-full items-center justify-center rounded-button border border-line bg-card text-sm font-bold transition hover:bg-page disabled:opacity-50"
        >
          {loadingMore ? "불러오는 중…" : "리뷰 더 보기"}
        </button>
      )}
    </section>
  );
}
