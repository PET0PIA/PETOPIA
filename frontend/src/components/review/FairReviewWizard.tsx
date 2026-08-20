import { useEffect, useMemo, useState } from "react";
import { Check, PawPrint, ShoppingBag, Sparkles, Users } from "lucide-react";
import { Button } from "../ui/Button";
import { Card } from "../ui/Card";
import { ApiError } from "../../api/client";
import { getActiveFeedbackTags, type FeedbackTagItem } from "../../api/feedbackTags";
import { getMyVisitedBooths, type BoothVisitResponse } from "../../api/booth";
import {
  getMyReviewStatus,
  submitFairReview,
  type BoothFeedbackSubmission,
  type CompanionType,
  type PurchaseBehavior,
  type VisitPurpose,
} from "../../api/fairReviewSubmission";

const MAX_BOOTHS = 3;

const companionTypeOptions: { value: CompanionType; label: string }[] = [
  { value: "ALONE", label: "혼자" },
  { value: "WITH_PET", label: "반려동물과 함께" },
  { value: "WITH_FAMILY", label: "가족과 함께" },
  { value: "WITH_FRIEND", label: "친구와 함께" },
];

const visitPurposeOptions: { value: VisitPurpose; label: string }[] = [
  { value: "SHOPPING", label: "쇼핑" },
  { value: "EXPERIENCE", label: "체험" },
  { value: "INFO", label: "정보 습득" },
  { value: "ETC", label: "기타" },
];

const purchaseBehaviorOptions: { value: PurchaseBehavior; label: string }[] = [
  { value: "PURCHASED", label: "구매했어요" },
  { value: "FOLLOWED_SNS", label: "SNS를 팔로우했어요" },
  { value: "LOOKED_ONLY", label: "둘러보기만 했어요" },
];

// V39__unified_review_feedback.sql의 CK_FEEDBACK_TAGS_CATEGORY와 맞춘다.
const fairCategoryLabels: Record<string, string> = {
  GUIDE_OPERATION: "안내/운영",
  SAFETY_HYGIENE: "안전/위생",
  WAIT_FLOW: "대기/동선",
  PET_CONVENIENCE: "반려동물 동반 편의성",
  FACILITY: "시설/편의",
  PRICE_VALUE: "가격/가치",
  CONTENT_PROGRAM: "부대행사/콘텐츠",
};

const boothCategoryLabels: Record<string, string> = {
  CONSULTATION: "상담/응대",
  PRODUCT: "상품/구성",
  EXPERIENCE: "체험/시연",
  PRICE_BENEFIT: "가격/혜택",
  BOOTH_ENVIRONMENT: "부스 환경",
};

const STEP_LABELS: Partial<Record<Step, string>> = {
  persona: "1. 방문 정보",
  "fair-tags": "2. 행사 평가",
  booths: "3. 부스 선택",
  "booth-feedback": "4. 부스 평가",
  revisit: "5. 재방문 의향",
};

type Step = "loading" | "load-error" | "already-reviewed" | "not-visited" | "persona" | "fair-tags" | "booths" | "booth-feedback" | "revisit" | "done";

interface BoothFeedbackState {
  tagIds: Set<number>;
  purchaseBehavior: PurchaseBehavior | null;
}

function groupByCategory(tags: FeedbackTagItem[]): Record<string, FeedbackTagItem[]> {
  const grouped: Record<string, FeedbackTagItem[]> = {};
  for (const tag of tags) {
    (grouped[tag.category] ??= []).push(tag);
  }
  return grouped;
}

/**
 * 태그 기반 통합 리뷰(V39) 작성 마법사. 페르소나(동반유형/방문목적) → 행사 전체 태그 →
 * 방문한 부스 선택(최대 3개) → 부스별 태그+구매행동 반복 → 재방문의향 → 제출 순서로
 * 진행한다. 아직 어느 페이지에도 연결돼 있지 않다 - FairDetailPage 연동은 별도 협의가
 * 필요한 Phase 5에서 진행한다(petopia-review-feature-plan 스킬 참고).
 */
export function FairReviewWizard({ fairId, onComplete }: { fairId: number; onComplete?: () => void }) {
  const [step, setStep] = useState<Step>("loading");
  const [loadError, setLoadError] = useState<string | null>(null);

  const [fairTags, setFairTags] = useState<FeedbackTagItem[]>([]);
  const [boothTags, setBoothTags] = useState<FeedbackTagItem[]>([]);
  const [visitedBooths, setVisitedBooths] = useState<BoothVisitResponse[]>([]);

  const [companionType, setCompanionType] = useState<CompanionType | null>(null);
  const [visitPurpose, setVisitPurpose] = useState<VisitPurpose | null>(null);
  const [selectedFairTagIds, setSelectedFairTagIds] = useState<Set<number>>(new Set());
  const [selectedBoothIds, setSelectedBoothIds] = useState<number[]>([]);
  const [boothFeedbacks, setBoothFeedbacks] = useState<Record<number, BoothFeedbackState>>({});
  const [boothFeedbackIndex, setBoothFeedbackIndex] = useState(0);
  const [wouldRevisit, setWouldRevisit] = useState<boolean | null>(null);

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  // 이미 작성했는지 + 태그 마스터(FAIR·BOOTH) + 방문한 부스 목록을 한 번에 불러온다.
  useEffect(() => {
    let alive = true;
    Promise.all([getMyReviewStatus(fairId), getActiveFeedbackTags("FAIR"), getActiveFeedbackTags("BOOTH"), getMyVisitedBooths(fairId)])
      .then(([status, fair, booth, booths]) => {
        if (!alive) return;
        if (status.alreadyReviewed) {
          setStep("already-reviewed");
          return;
        }
        if (!status.hasVisited) {
          setStep("not-visited");
          return;
        }
        setFairTags(fair);
        setBoothTags(booth);
        setVisitedBooths(booths);
        setStep("persona");
      })
      .catch((err) => {
        if (!alive) return;
        setLoadError(err instanceof ApiError ? err.message : "리뷰 작성 화면을 불러오지 못했어요.");
        setStep("load-error");
      });
    return () => {
      alive = false;
    };
  }, [fairId]);

  const fairTagsByCategory = useMemo(() => groupByCategory(fairTags), [fairTags]);
  const boothTagsByCategory = useMemo(() => groupByCategory(boothTags), [boothTags]);

  function toggleFairTag(tagId: number) {
    setSelectedFairTagIds((prev) => {
      const next = new Set(prev);
      if (next.has(tagId)) next.delete(tagId);
      else next.add(tagId);
      return next;
    });
  }

  function toggleBooth(boothId: number) {
    setSelectedBoothIds((prev) => {
      if (prev.includes(boothId)) return prev.filter((id) => id !== boothId);
      if (prev.length >= MAX_BOOTHS) return prev;
      return [...prev, boothId];
    });
  }

  function toggleBoothTag(boothId: number, tagId: number) {
    setBoothFeedbacks((prev) => {
      const current = prev[boothId] ?? { tagIds: new Set<number>(), purchaseBehavior: null };
      const nextTagIds = new Set(current.tagIds);
      if (nextTagIds.has(tagId)) nextTagIds.delete(tagId);
      else nextTagIds.add(tagId);
      return { ...prev, [boothId]: { ...current, tagIds: nextTagIds } };
    });
  }

  function setBoothPurchaseBehavior(boothId: number, value: PurchaseBehavior | null) {
    setBoothFeedbacks((prev) => {
      const current = prev[boothId] ?? { tagIds: new Set<number>(), purchaseBehavior: null };
      return { ...prev, [boothId]: { ...current, purchaseBehavior: value } };
    });
  }

  function goToBoothFeedbackOrRevisit() {
    if (selectedBoothIds.length === 0) {
      setStep("revisit");
      return;
    }
    setBoothFeedbackIndex(0);
    setStep("booth-feedback");
  }

  function nextBoothOrRevisit() {
    if (boothFeedbackIndex + 1 < selectedBoothIds.length) {
      setBoothFeedbackIndex((i) => i + 1);
    } else {
      setStep("revisit");
    }
  }

  function handleSubmit() {
    if (companionType == null || visitPurpose == null || wouldRevisit == null) return;
    setSubmitting(true);
    setSubmitError(null);
    const booths: BoothFeedbackSubmission[] = selectedBoothIds.map((boothId) => {
      const feedback = boothFeedbacks[boothId];
      return {
        boothId,
        tagIds: feedback ? Array.from(feedback.tagIds) : [],
        purchaseBehavior: feedback?.purchaseBehavior ?? null,
      };
    });
    submitFairReview(fairId, {
      companionType,
      visitPurpose,
      wouldRevisit,
      fairTagIds: Array.from(selectedFairTagIds),
      booths,
    })
      .then(() => setStep("done"))
      .catch((err) => setSubmitError(err instanceof ApiError ? err.message : "리뷰를 제출하지 못했어요."))
      .finally(() => setSubmitting(false));
  }

  if (step === "loading") {
    return <div className="surface grid min-h-72 place-items-center text-sm text-muted">불러오는 중이에요...</div>;
  }

  if (step === "load-error") {
    return <div className="surface p-6 text-sm font-bold text-primary-strong">{loadError}</div>;
  }

  if (step === "already-reviewed") {
    return (
      <Card className="p-6 text-center">
        <p className="text-sm font-bold text-ink">이미 이 행사에 리뷰를 남겼어요.</p>
        <p className="mt-1 text-sm text-muted">행사당 리뷰는 1건만 작성할 수 있어요.</p>
      </Card>
    );
  }

  if (step === "not-visited") {
    return (
      <Card className="p-6 text-center">
        <p className="text-sm font-bold text-ink">아직 이 행사를 방문하지 않으셨어요.</p>
        <p className="mt-1 text-sm text-muted">행사에 입장한 기록이 있어야 리뷰를 작성할 수 있어요.</p>
      </Card>
    );
  }

  if (step === "done") {
    return (
      <Card className="p-8 text-center">
        <div className="mx-auto mb-4 grid size-14 place-items-center rounded-full bg-leaf-soft text-ink">
          <Check size={26} aria-hidden="true" />
        </div>
        <h2 className="text-lg font-extrabold">리뷰가 등록됐어요</h2>
        <p className="mt-2 text-sm text-muted">소중한 후기 감사해요!</p>
        {onComplete && (
          <Button className="mt-6" onClick={onComplete}>
            확인
          </Button>
        )}
      </Card>
    );
  }

  const stepLabel = STEP_LABELS[step];
  const currentBoothId = selectedBoothIds[boothFeedbackIndex];

  return (
    <div className="flex flex-col gap-4">
      {stepLabel && <p className="text-xs font-bold text-muted">{stepLabel}</p>}

      {step === "persona" && (
        <Card className="p-6">
          <h2 className="text-base font-extrabold">누구와, 어떤 목적으로 방문하셨나요?</h2>
          <div className="mt-5">
            <span className="mb-2 flex items-center gap-1.5 text-sm font-bold text-ink">
              <Users size={16} aria-hidden="true" />
              동반유형
            </span>
            <OptionGrid options={companionTypeOptions} value={companionType} onChange={setCompanionType} />
          </div>
          <div className="mt-5">
            <span className="mb-2 flex items-center gap-1.5 text-sm font-bold text-ink">
              <ShoppingBag size={16} aria-hidden="true" />
              방문목적
            </span>
            <OptionGrid options={visitPurposeOptions} value={visitPurpose} onChange={setVisitPurpose} />
          </div>
          <div className="mt-6 flex justify-end">
            <Button disabled={companionType == null || visitPurpose == null} onClick={() => setStep("fair-tags")}>
              다음
            </Button>
          </div>
        </Card>
      )}

      {step === "fair-tags" && (
        <Card className="p-6">
          <h2 className="text-base font-extrabold">행사는 어떠셨나요?</h2>
          <p className="mt-1 text-sm text-muted">해당하는 항목을 자유롭게 골라주세요. (선택 사항)</p>
          <div className="mt-5 flex flex-col gap-5">
            {Object.entries(fairTagsByCategory).map(([category, tags]) => (
              <TagCategoryGroup key={category} title={fairCategoryLabels[category] ?? category} tags={tags} selectedIds={selectedFairTagIds} onToggle={toggleFairTag} />
            ))}
          </div>
          <div className="mt-6 flex justify-between">
            <Button variant="outline" onClick={() => setStep("persona")}>
              이전
            </Button>
            <Button onClick={() => setStep("booths")}>다음</Button>
          </div>
        </Card>
      )}

      {step === "booths" && (
        <Card className="p-6">
          <h2 className="text-base font-extrabold">방문한 부스도 평가해 주세요</h2>
          <p className="mt-1 text-sm text-muted">최대 {MAX_BOOTHS}개까지 선택할 수 있어요. (선택 사항)</p>
          {visitedBooths.length === 0 ? (
            <p className="mt-6 py-6 text-center text-sm text-muted">방문 이력이 확인된 부스가 없어요.</p>
          ) : (
            <div className="mt-5 grid gap-3 sm:grid-cols-2">
              {visitedBooths.map((booth) => {
                const selected = selectedBoothIds.includes(booth.boothId);
                const disabled = !selected && selectedBoothIds.length >= MAX_BOOTHS;
                return (
                  <button
                    key={booth.boothId}
                    type="button"
                    onClick={() => toggleBooth(booth.boothId)}
                    disabled={disabled}
                    className={`flex items-center gap-3 rounded-card border p-3 text-left transition disabled:cursor-not-allowed disabled:opacity-50 ${
                      selected ? "border-primary-strong bg-primary-soft" : "border-line bg-card hover:bg-page"
                    }`}
                  >
                    {booth.imageUrl ? (
                      <img src={booth.imageUrl} alt="" className="size-12 shrink-0 rounded-lg object-cover" />
                    ) : (
                      <div className="grid size-12 shrink-0 place-items-center rounded-lg bg-surface-alt text-muted">
                        <PawPrint size={18} aria-hidden="true" />
                      </div>
                    )}
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-bold text-ink">{booth.name}</span>
                      <span className="block text-xs text-muted">방문 {booth.visitCount}회</span>
                    </span>
                    {selected && <Check size={18} className="shrink-0 text-primary-strong" aria-hidden="true" />}
                  </button>
                );
              })}
            </div>
          )}
          <div className="mt-6 flex justify-between">
            <Button variant="outline" onClick={() => setStep("fair-tags")}>
              이전
            </Button>
            <Button onClick={goToBoothFeedbackOrRevisit}>다음</Button>
          </div>
        </Card>
      )}

      {step === "booth-feedback" && currentBoothId != null && (
        <BoothFeedbackStep
          booth={visitedBooths.find((b) => b.boothId === currentBoothId)}
          tagsByCategory={boothTagsByCategory}
          feedback={boothFeedbacks[currentBoothId] ?? { tagIds: new Set(), purchaseBehavior: null }}
          index={boothFeedbackIndex}
          total={selectedBoothIds.length}
          onToggleTag={(tagId) => toggleBoothTag(currentBoothId, tagId)}
          onChangePurchaseBehavior={(value) => setBoothPurchaseBehavior(currentBoothId, value)}
          onPrev={() => {
            if (boothFeedbackIndex === 0) setStep("booths");
            else setBoothFeedbackIndex((i) => i - 1);
          }}
          onNext={nextBoothOrRevisit}
        />
      )}

      {step === "revisit" && (
        <Card className="p-6">
          <h2 className="text-base font-extrabold">
            <Sparkles size={16} className="mr-1.5 inline align-[-2px]" aria-hidden="true" />
            다음에도 이 행사를 방문하고 싶으신가요?
          </h2>
          <div className="mt-5 flex gap-3">
            <Button variant={wouldRevisit === true ? "primary" : "outline"} className="flex-1" onClick={() => setWouldRevisit(true)}>
              네, 또 올게요
            </Button>
            <Button variant={wouldRevisit === false ? "primary" : "outline"} className="flex-1" onClick={() => setWouldRevisit(false)}>
              아니요
            </Button>
          </div>
          {submitError && <p className="mt-4 text-sm font-bold text-primary-strong">{submitError}</p>}
          <div className="mt-6 flex justify-between">
            <Button variant="outline" onClick={() => setStep(selectedBoothIds.length > 0 ? "booth-feedback" : "booths")} disabled={submitting}>
              이전
            </Button>
            <Button onClick={handleSubmit} disabled={wouldRevisit == null || submitting}>
              {submitting ? "제출 중…" : "리뷰 제출"}
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
}

/** 동반유형·방문목적처럼 필수·단일선택인 항목의 버튼 그리드. */
function OptionGrid<T extends string>({ options, value, onChange }: { options: { value: T; label: string }[]; value: T | null; onChange: (value: T) => void }) {
  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          className={`min-h-11 rounded-button border px-3 text-sm font-bold transition ${
            value === option.value ? "border-primary-strong bg-primary-soft text-primary-strong" : "border-line bg-card text-ink hover:bg-page"
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

/** 카테고리 하나에 속한 태그들을 칩 형태로 다중선택하는 그룹. */
function TagCategoryGroup({ title, tags, selectedIds, onToggle }: { title: string; tags: FeedbackTagItem[]; selectedIds: Set<number>; onToggle: (tagId: number) => void }) {
  return (
    <div>
      <p className="mb-2 text-sm font-bold text-ink">{title}</p>
      <div className="flex flex-wrap gap-2">
        {tags.map((tag) => {
          const selected = selectedIds.has(tag.tagId);
          return (
            <button
              key={tag.tagId}
              type="button"
              onClick={() => onToggle(tag.tagId)}
              className={`rounded-full border px-3 py-1.5 text-xs font-bold transition ${
                selected ? "border-primary-strong bg-primary-soft text-primary-strong" : "border-line bg-card text-muted hover:bg-page"
              }`}
            >
              {tag.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/** 부스 하나에 대한 태그 선택 + 구매행동. 선택된 부스 수만큼 반복해서 보여준다. */
function BoothFeedbackStep({
  booth,
  tagsByCategory,
  feedback,
  index,
  total,
  onToggleTag,
  onChangePurchaseBehavior,
  onPrev,
  onNext,
}: {
  booth: BoothVisitResponse | undefined;
  tagsByCategory: Record<string, FeedbackTagItem[]>;
  feedback: BoothFeedbackState;
  index: number;
  total: number;
  onToggleTag: (tagId: number) => void;
  onChangePurchaseBehavior: (value: PurchaseBehavior | null) => void;
  onPrev: () => void;
  onNext: () => void;
}) {
  return (
    <Card className="p-6">
      <p className="text-xs font-bold text-muted">
        부스 평가 {index + 1}/{total}
      </p>
      <h2 className="mt-1 text-base font-extrabold">{booth?.name ?? "부스"}</h2>
      <div className="mt-5 flex flex-col gap-5">
        {Object.entries(tagsByCategory).map(([category, tags]) => (
          <TagCategoryGroup key={category} title={boothCategoryLabels[category] ?? category} tags={tags} selectedIds={feedback.tagIds} onToggle={onToggleTag} />
        ))}
      </div>
      <div className="mt-6">
        <p className="mb-2 text-sm font-bold text-ink">이 부스에서 구매하셨나요? (선택 사항)</p>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
          {purchaseBehaviorOptions.map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => onChangePurchaseBehavior(feedback.purchaseBehavior === option.value ? null : option.value)}
              className={`min-h-11 rounded-button border px-3 text-sm font-bold transition ${
                feedback.purchaseBehavior === option.value ? "border-primary-strong bg-primary-soft text-primary-strong" : "border-line bg-card text-ink hover:bg-page"
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>
      <div className="mt-6 flex justify-between">
        <Button variant="outline" onClick={onPrev}>
          이전
        </Button>
        <Button onClick={onNext}>{index + 1 === total ? "다음" : "다음 부스"}</Button>
      </div>
    </Card>
  );
}
