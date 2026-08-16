import { useState, type FormEvent } from "react";
import { Star } from "lucide-react";
import { Button } from "../ui/Button";
import { Textarea } from "../ui/Textarea";
import { ApiError } from "../../api/client";
import { createFairReview, updateFairReview, type FairReviewWriteResult } from "../../api/fairReviewActions";

/** 백엔드 RV005(REVIEW_CONTENT_TOO_LONG) 상한과 맞춘다. */
const MAX_CONTENT_LENGTH = 1000;

interface FairReviewFormProps {
  fairId: number;
  /** 수정 모드일 때만 전달한다. 있으면 PATCH, 없으면 새 리뷰 POST. */
  reviewId?: number;
  initialRating?: number;
  initialContent?: string;
  submitLabel?: string;
  onSuccess: (result: FairReviewWriteResult) => void;
  onCancel?: () => void;
}

/**
 * 리뷰 작성/수정 폼. 독립 모듈이라 아직 어느 페이지에도 연결돼 있지 않다 - 실제 화면(행사
 * 상세 페이지, 마이페이지 "내 리뷰")에 붙이는 작업은 각 페이지 담당자와 협의해 별도로
 * 진행한다(petopia-review-feature-plan 스킬 참고). 작성·수정 양쪽에서 재사용할 수 있게
 * reviewId 유무로 모드를 나눴다.
 */
export function FairReviewForm({ fairId, reviewId, initialRating = 0, initialContent = "", submitLabel, onSuccess, onCancel }: FairReviewFormProps) {
  const [rating, setRating] = useState(initialRating);
  const [content, setContent] = useState(initialContent);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isEdit = reviewId != null;
  const overLimit = content.length > MAX_CONTENT_LENGTH;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (rating < 1 || rating > 5) {
      setError("별점을 선택해주세요.");
      return;
    }
    if (content.trim().length === 0) {
      setError("리뷰 내용을 입력해주세요.");
      return;
    }
    if (overLimit) {
      setError(`리뷰 내용은 ${MAX_CONTENT_LENGTH}자를 초과할 수 없어요.`);
      return;
    }

    setError(null);
    setSubmitting(true);
    const request = isEdit ? updateFairReview(fairId, reviewId, { rating, content }) : createFairReview(fairId, { rating, content });
    request
      .then((result) => onSuccess(result))
      .catch((err) => setError(err instanceof ApiError ? err.message : "리뷰를 저장하지 못했어요."))
      .finally(() => setSubmitting(false));
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <span className="mb-1.5 block text-sm font-bold text-ink">
          별점<span className="ml-1 text-primary-strong">*</span>
        </span>
        <StarPicker value={rating} onChange={setRating} disabled={submitting} />
      </div>

      <div>
        <label htmlFor="fair-review-content" className="mb-1.5 block text-sm font-bold text-ink">
          리뷰 내용<span className="ml-1 text-primary-strong">*</span>
        </label>
        <Textarea
          id="fair-review-content"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="행사는 어떠셨나요? 다른 분들에게 도움이 될 후기를 남겨주세요."
          disabled={submitting}
        />
        <p className={`mt-1 text-right text-xs ${overLimit ? "font-bold text-primary-strong" : "text-muted"}`}>
          {content.length}/{MAX_CONTENT_LENGTH}
        </p>
      </div>

      {error && <p className="text-sm font-bold text-primary-strong">{error}</p>}

      <div className="flex justify-end gap-2 pt-2">
        {onCancel && (
          <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
            취소
          </Button>
        )}
        <Button type="submit" disabled={submitting}>
          {submitting ? "저장 중…" : (submitLabel ?? (isEdit ? "수정하기" : "리뷰 등록"))}
        </Button>
      </div>
    </form>
  );
}

/** 1~5점 별점 선택기. 클릭한 별까지 채운다. */
function StarPicker({ value, onChange, disabled }: { value: number; onChange: (rating: number) => void; disabled?: boolean }) {
  return (
    <div className="flex gap-1" role="radiogroup" aria-label="별점 선택">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          role="radio"
          aria-checked={value === n}
          aria-label={`${n}점`}
          disabled={disabled}
          onClick={() => onChange(n)}
          className="p-0.5 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Star size={24} fill={n <= value ? "currentColor" : "none"} className={n <= value ? "text-sun" : "text-muted"} aria-hidden="true" />
        </button>
      ))}
    </div>
  );
}
