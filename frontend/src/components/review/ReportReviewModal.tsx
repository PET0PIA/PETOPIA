import { useState } from "react";
import { Button } from "../ui/Button";
import { Dialog } from "../ui/Dialog";
import { Textarea } from "../ui/Textarea";
import { ApiError } from "../../api/client";
import { reportFairReview, type FairReviewReportReason } from "../../api/fairReviewActions";

const REASON_OPTIONS: { value: FairReviewReportReason; label: string }[] = [
  { value: "SPAM", label: "광고·스팸" },
  { value: "ABUSE", label: "욕설·비방" },
  { value: "FALSE_INFO", label: "허위 내용" },
  { value: "ETC", label: "기타" },
];

interface ReportReviewModalProps {
  open: boolean;
  onClose: () => void;
  fairId: number;
  reviewId: number;
  onReported?: () => void;
}

/**
 * 리뷰 신고 모달. 독립 모듈이라 아직 어느 화면에도 연결돼 있지 않다 - 리뷰 목록(현재
 * FairReviews.tsx가 갖고 있음) 항목마다 신고 버튼을 붙이는 작업은 그 화면 담당자와 협의해
 * 별도로 진행한다(petopia-review-feature-plan 스킬 참고).
 */
export function ReportReviewModal({ open, onClose, fairId, reviewId, onReported }: ReportReviewModalProps) {
  const [reason, setReason] = useState<FairReviewReportReason>("SPAM");
  const [detail, setDetail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  function reset() {
    setReason("SPAM");
    setDetail("");
    setError(null);
    setDone(false);
  }

  function handleClose() {
    reset();
    onClose();
  }

  function handleSubmit() {
    if (reason === "ETC" && detail.trim().length === 0) {
      setError("기타 사유는 상세 내용을 입력해주세요.");
      return;
    }
    setError(null);
    setSubmitting(true);
    reportFairReview(fairId, reviewId, { reason, reasonDetail: reason === "ETC" ? detail : undefined })
      .then(() => setDone(true))
      .catch((err) => {
        if (err instanceof ApiError && err.status === 409) {
          setError("이미 신고한 리뷰예요.");
        } else {
          setError(err instanceof ApiError ? err.message : "신고를 접수하지 못했어요.");
        }
      })
      .finally(() => setSubmitting(false));
  }

  return (
    <Dialog open={open} onClose={handleClose} title="리뷰 신고">
      {done ? (
        <div className="space-y-4">
          <p className="text-sm text-ink">신고가 접수됐어요. 검토 후 조치할게요.</p>
          <div className="flex justify-end">
            <Button
              type="button"
              onClick={() => {
                handleClose();
                onReported?.();
              }}
            >
              확인
            </Button>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <div className="flex flex-col gap-2">
            {REASON_OPTIONS.map((option) => (
              <label key={option.value} className="flex items-center gap-2 text-sm">
                <input
                  type="radio"
                  name="report-reason"
                  value={option.value}
                  checked={reason === option.value}
                  onChange={() => setReason(option.value)}
                  disabled={submitting}
                />
                {option.label}
              </label>
            ))}
          </div>

          {reason === "ETC" && (
            <Textarea
              value={detail}
              onChange={(e) => setDetail(e.target.value)}
              placeholder="신고 사유를 자세히 적어주세요."
              disabled={submitting}
            />
          )}

          {error && <p className="text-sm font-bold text-primary-strong">{error}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={handleClose} disabled={submitting}>
              취소
            </Button>
            <Button type="button" onClick={handleSubmit} disabled={submitting}>
              {submitting ? "접수 중…" : "신고하기"}
            </Button>
          </div>
        </div>
      )}
    </Dialog>
  );
}
