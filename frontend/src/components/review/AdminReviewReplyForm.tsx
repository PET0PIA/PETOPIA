import { useEffect, useState, type FormEvent } from "react";
import { Button } from "../ui/Button";
import { Textarea } from "../ui/Textarea";
import { ApiError } from "../../api/client";
import { createFairReviewReply, getFairReviewReply, updateFairReviewReply } from "../../api/fairReviewActions";

// "2026-08-14T10:30:00" → "2026.08.14"
function formatReplyDate(iso: string): string {
  return /^\d{4}-\d{2}-\d{2}/.test(iso) ? iso.slice(0, 10).replace(/-/g, ".") : iso;
}

interface AdminReviewReplyFormProps {
  fairId: number;
  reviewId: number;
}

/**
 * 행사 담당자용 리뷰 답글 작성/수정 폼. 독립 모듈이라 아직 fair-admin 화면에 연결돼 있지
 * 않다 - 실제로 붙이는 작업은 fair-admin 도메인 담당자와 협의해 별도로 진행한다
 * (petopia-review-feature-plan 스킬 참고). 답글이 이미 있으면 조회해서 수정 모드로,
 * 없으면 작성 모드로 시작한다.
 */
export function AdminReviewReplyForm({ fairId, reviewId }: AdminReviewReplyFormProps) {
  const [content, setContent] = useState("");
  const [existingUpdatedAt, setExistingUpdatedAt] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    getFairReviewReply(fairId, reviewId)
      .then((reply) => {
        if (!alive) return;
        setContent(reply.content);
        setExistingUpdatedAt(reply.updatedAt);
      })
      .catch((err) => {
        if (!alive) return;
        // 404(REVIEW_REPLY_NOT_FOUND)는 "아직 답글 없음"이라는 정상 상태다.
        if (!(err instanceof ApiError && err.status === 404)) {
          setError(err instanceof ApiError ? err.message : "답글을 불러오지 못했어요.");
        }
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [fairId, reviewId]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (content.trim().length === 0) {
      setError("답글 내용을 입력해주세요.");
      return;
    }
    setError(null);
    setSubmitting(true);
    const request = existingUpdatedAt != null ? updateFairReviewReply(fairId, reviewId, content) : createFairReviewReply(fairId, reviewId, content);
    request
      .then((reply) => {
        setContent(reply.content);
        setExistingUpdatedAt(reply.updatedAt);
        setEditing(false);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "답글을 저장하지 못했어요."))
      .finally(() => setSubmitting(false));
  }

  if (loading) {
    return <div className="h-20 animate-pulse rounded-card bg-surface-alt" aria-hidden="true" />;
  }

  if (existingUpdatedAt != null && !editing) {
    return (
      <div className="rounded-card border border-line bg-surface-alt p-3">
        <div className="flex items-center justify-between gap-2">
          <span className="text-xs font-bold text-muted">담당자 답글 · {formatReplyDate(existingUpdatedAt)}</span>
          <button type="button" onClick={() => setEditing(true)} className="text-xs font-bold text-muted hover:text-ink">
            수정
          </button>
        </div>
        <p className="mt-1 whitespace-pre-line text-sm leading-relaxed text-ink">{content}</p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <Textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder="리뷰에 답글을 남겨주세요."
        disabled={submitting}
      />
      {error && <p className="text-sm font-bold text-primary-strong">{error}</p>}
      <div className="flex justify-end gap-2">
        {existingUpdatedAt != null && (
          <Button type="button" variant="outline" onClick={() => setEditing(false)} disabled={submitting}>
            취소
          </Button>
        )}
        <Button type="submit" disabled={submitting}>
          {submitting ? "저장 중…" : existingUpdatedAt != null ? "수정하기" : "답글 등록"}
        </Button>
      </div>
    </form>
  );
}
