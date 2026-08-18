import { useEffect, useRef, useState } from "react";
import { Flag, Star } from "lucide-react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import { ApiError } from "../../api/client";
import { getFairReviewSummary, getFairReviews, type FairReviewListItem, type FairReviewSummary } from "../../api/review";
import { getFairReviewReply, type FairReviewReply } from "../../api/fairReviewActions";
import { FairReviewForm } from "../../components/review/FairReviewForm";
import { ReportReviewModal } from "../../components/review/ReportReviewModal";
import { Button } from "../../components/ui/Button";

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
 * verifiedVisit은 백엔드가 아직 항상 false라 화면에 쓰지 않는다.
 *
 * 리뷰가 0개고 비로그인 상태면 섹션 자체를 숨긴다(부가 정보라 빈 섹션으로 페이지를 늘리지
 * 않는다). 로그인 상태면 리뷰가 0개여도 "첫 리뷰를 남겨보세요" 형태로 작성 진입을 보여준다.
 *
 * 작성/신고는 review 도메인의 독립 모듈(components/review/*)을 그대로 붙여 쓴다 - 그
 * 모듈들이 쓰는 쓰기 API(api/fairReviewActions.ts)는 이 파일이 원래 쓰던 조회 전용
 * api/review.ts와 분리된 별도 파일이다.
 */
export function FairReviews({ fairId }: { fairId: number }) {
  const { status } = useAuth();
  const loggedIn = status === "authenticated";
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [summary, setSummary] = useState<FairReviewSummary | null>(null);
  const [items, setItems] = useState<FairReviewListItem[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  // 밖에서 곧바로 작성 폼을 열고 싶을 때 ?write-review=1 을 달고 들어온다(예: 예약 상세의
  // "후기 작성"). 로그인 확인이 끝나기 전엔 loggedIn이 false라, 이 값은 "작성하러 왔다"는
  // 의사만 담고 실제 노출은 로그인 상태와 함께 showForm에서 판단한다.
  const [writeIntent, setWriteIntent] = useState(searchParams.get("write-review") === "1");
  const showForm = writeIntent && loggedIn;
  const [reportingReviewId, setReportingReviewId] = useState<number | null>(null);
  // 리뷰별 담당자 답글. 아직 안 불러왔으면 키가 없고, 불러왔는데 답글이 없으면 null이다.
  const [replies, setReplies] = useState<Record<number, FairReviewReply | null>>({});
  const sectionRef = useRef<HTMLElement | null>(null);
  const scrolledRef = useRef(false);

  // ?write-review=1 로 들어온 경우 리뷰 자리로 스크롤한다. 목록 로딩이 끝나야 섹션이 DOM에
  // 생기므로 loading이 풀린 뒤에, 그리고 한 번만 움직인다(사용자가 스크롤한 뒤 되돌리지 않게).
  useEffect(() => {
    if (loading || scrolledRef.current || searchParams.get("write-review") !== "1") return;
    scrolledRef.current = true;
    sectionRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [loading, searchParams]);

  // 넘겨받은 리뷰들의 답글을 병렬로 채운다. 답글이 없으면(404) null로 기록해서 "다시 안 불러옴"을
  // 표시한다. 그 외 오류(네트워크·서버 오류)는 기존 값을 그대로 두어 다음 재조회 때 다시 시도되게 한다 -
  // 안 그러면 네트워크 오류로도 있던 답글이 "없음"으로 캐시돼 사라져 보일 수 있다.
  function loadReplies(reviewIds: number[]) {
    Promise.allSettled(reviewIds.map((reviewId) => getFairReviewReply(fairId, reviewId))).then((results) => {
      setReplies((prev) => {
        const next = { ...prev };
        results.forEach((result, index) => {
          const id = reviewIds[index];
          if (result.status === "fulfilled") {
            next[id] = result.value;
          } else if (result.reason instanceof ApiError && result.reason.status === 404) {
            next[id] = null;
          }
        });
        return next;
      });
    });
  }

  useEffect(() => {
    let alive = true;
    Promise.allSettled([getFairReviewSummary(fairId), getFairReviews(fairId, 0, PAGE_SIZE)]).then(([summaryRes, listRes]) => {
      if (!alive) return;
      if (summaryRes.status === "fulfilled") setSummary(summaryRes.value);
      if (listRes.status === "fulfilled") {
        setItems(listRes.value.items);
        setHasNext(listRes.value.hasNext);
        setPage(0);
        loadReplies(listRes.value.items.map((item) => item.reviewId));
      }
      setLoading(false);
    });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fairId]);

  function loadMore() {
    const nextPage = page + 1;
    setLoadingMore(true);
    getFairReviews(fairId, nextPage, PAGE_SIZE)
      .then((res) => {
        setItems((prev) => [...prev, ...res.items]);
        setHasNext(res.hasNext);
        setPage(nextPage);
        loadReplies(res.items.map((item) => item.reviewId));
      })
      .catch(() => {
        /* 더 보기 실패는 조용히 무시 - 이미 보이는 목록은 유지 */
      })
      .finally(() => setLoadingMore(false));
  }

  // 새 리뷰 작성 성공 후 목록·요약을 처음부터 다시 불러온다(방금 쓴 리뷰가 최신순 맨 앞에 오게).
  function reload() {
    Promise.allSettled([getFairReviewSummary(fairId), getFairReviews(fairId, 0, PAGE_SIZE)]).then(([summaryRes, listRes]) => {
      if (summaryRes.status === "fulfilled") setSummary(summaryRes.value);
      if (listRes.status === "fulfilled") {
        setItems(listRes.value.items);
        setHasNext(listRes.value.hasNext);
        setPage(0);
        loadReplies(listRes.value.items.map((item) => item.reviewId));
      }
    });
  }

  function handleWriteClick() {
    if (!loggedIn) {
      navigate("/login");
      return;
    }
    setWriteIntent((prev) => !prev);
  }

  const count = summary?.reviewCount ?? 0;
  if (loading) return null;
  // 로그인 안 했고 리뷰도 없으면 섹션 자체를 숨긴다. 로그인 상태면 0개여도 작성 진입을 보여준다.
  if (count === 0 && !loggedIn) return null;

  return (
    <section id="reviews" ref={sectionRef} className="mt-10 scroll-mt-24">
      <div className="flex items-baseline justify-between gap-3">
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
        <Button type="button" variant="outline" onClick={handleWriteClick}>
          {showForm ? "취소" : "리뷰 작성"}
        </Button>
      </div>

      {showForm && (
        <div className="mt-4 rounded-card border border-line bg-card p-4">
          <FairReviewForm
            fairId={fairId}
            submitLabel="리뷰 등록"
            onCancel={() => setWriteIntent(false)}
            onSuccess={() => {
              setWriteIntent(false);
              reload();
            }}
          />
        </div>
      )}

      {count === 0 ? (
        <p className="mt-4 py-6 text-center text-sm text-muted">아직 등록된 리뷰가 없어요. 첫 리뷰를 남겨보세요!</p>
      ) : (
        <ul className="mt-4 flex flex-col gap-4">
          {items.map((review) => {
            const reply = replies[review.reviewId];
            return (
              <li key={review.reviewId} className="border-b border-line pb-4 last:border-0">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-bold">{review.nickname}</span>
                  <span className="text-xs text-muted">{formatReviewDate(review.createdAt)}</span>
                </div>
                <div className="mt-1">
                  <StarRating rating={review.rating} />
                </div>
                <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-ink">{review.content}</p>

                {reply && (
                  <div className="mt-3 rounded-button bg-surface-alt p-3">
                    <span className="text-xs font-bold text-muted">담당자 답글 · {formatReviewDate(reply.updatedAt)}</span>
                    <p className="mt-1 whitespace-pre-line text-sm leading-relaxed text-ink">{reply.content}</p>
                  </div>
                )}

                <button
                  type="button"
                  onClick={() => {
                    if (!loggedIn) {
                      navigate("/login");
                      return;
                    }
                    setReportingReviewId(review.reviewId);
                  }}
                  className="mt-2 inline-flex items-center gap-1 text-xs text-muted hover:text-primary-strong"
                >
                  <Flag size={12} aria-hidden="true" />
                  신고
                </button>
              </li>
            );
          })}
        </ul>
      )}

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

      {reportingReviewId != null && (
        <ReportReviewModal
          open
          fairId={fairId}
          reviewId={reportingReviewId}
          onClose={() => setReportingReviewId(null)}
        />
      )}
    </section>
  );
}
