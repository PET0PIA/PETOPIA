import { AlertCircle, CheckCircle2, Send } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, Navigate, useParams } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { SectionHeader } from "../../components/common/SectionHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { Select } from "../../components/ui/Select";
import { Textarea } from "../../components/ui/Textarea";
import { AttachmentUploadField } from "../../components/ui/AttachmentUploadField";
import { HallBoothMap } from "../../components/booth-map/HallBoothMap";
import { ApiError } from "../../api/client";
import { getMyBusinesses, type Business } from "../../api/business";
import {
  getBoothSlots,
  submitApplication,
  type ApplicationResponse,
  type ApplicationSubmitRequest,
  type BoothSlotLockStatus,
} from "../../api/application";

const MAX_SLOTS = 3;

interface FormState {
  businessId: string;
  purpose: string;
  itemsDesc: string;
  managerName: string;
  managerPhone: string;
  managerEmail: string;
  agreedTerms: boolean;
}

const initialForm: FormState = {
  businessId: "",
  purpose: "",
  itemsDesc: "",
  managerName: "",
  managerPhone: "",
  managerEmail: "",
  agreedTerms: false,
};

function label(htmlFor: string, text: string, required = false) {
  return <label htmlFor={htmlFor} className="mb-1.5 block text-sm font-bold text-ink">{text}{required && <span className="ml-1 text-primary-strong">*</span>}</label>;
}

// 홀별로 그룹핑한다 - posX/posY가 홀마다 다른 도면 기준 좌표라 섞어서 그리면 안 된다
// (RecruitNoticeDetailPage.groupByHall과 동일한 이유).
function groupByHall(slots: BoothSlotLockStatus[]) {
  const groups = new Map<number, { hallName: string; floorPlanImageUrl: string | null; slots: BoothSlotLockStatus[] }>();
  for (const slot of slots) {
    const group = groups.get(slot.hallId);
    if (group) {
      group.slots.push(slot);
    } else {
      groups.set(slot.hallId, { hallName: slot.hallName, floorPlanImageUrl: slot.floorPlanImageUrl, slots: [slot] });
    }
  }
  return [...groups.entries()].map(([hallId, group]) => ({ hallId, ...group }));
}

function validate(form: FormState, selectedSlotIds: number[]): string[] {
  const errors: string[] = [];
  if (form.businessId === "") errors.push("신청할 사업자를 선택해 주세요.");
  if (selectedSlotIds.length === 0) errors.push("부스 슬롯을 1개 이상 선택해 주세요.");
  if (selectedSlotIds.length > MAX_SLOTS) errors.push(`부스 슬롯은 최대 ${MAX_SLOTS}개까지 선택할 수 있어요.`);
  if (form.purpose.trim() === "") errors.push("참가 목적을 입력해 주세요.");
  else if (form.purpose.length > 200) errors.push("참가 목적은 200자 이하로 입력해 주세요.");
  if (form.itemsDesc.trim() === "") errors.push("판매·전시 품목을 입력해 주세요.");
  else if (form.itemsDesc.length > 500) errors.push("판매·전시 품목은 500자 이하로 입력해 주세요.");
  if (form.managerName.trim() === "") errors.push("신청 담당자명을 입력해 주세요.");
  else if (form.managerName.length > 50) errors.push("신청 담당자명은 50자 이하로 입력해 주세요.");
  if (form.managerPhone.trim() === "") errors.push("신청 담당자 연락처를 입력해 주세요.");
  else if (form.managerPhone.length > 20) errors.push("신청 담당자 연락처는 20자 이하로 입력해 주세요.");
  if (form.managerEmail.trim() === "") errors.push("신청 담당자 이메일을 입력해 주세요.");
  else if (form.managerEmail.length > 100) errors.push("신청 담당자 이메일은 100자 이하로 입력해 주세요.");
  if (!form.agreedTerms) errors.push("이용약관에 동의해야 신청할 수 있어요.");
  return errors;
}

function toRequest(form: FormState, selectedSlotIds: number[], attachmentObjectKey: string | null): ApplicationSubmitRequest {
  return {
    businessId: Number(form.businessId),
    boothSlotIds: selectedSlotIds,
    purpose: form.purpose.trim(),
    itemsDesc: form.itemsDesc.trim(),
    managerName: form.managerName.trim(),
    managerPhone: form.managerPhone.trim(),
    managerEmail: form.managerEmail.trim(),
    agreedTerms: form.agreedTerms,
    attachmentObjectKey: attachmentObjectKey ?? undefined,
  };
}

export function ApplicationSubmitPage() {
  const { fairId } = useParams<{ fairId: string }>();

  const [businesses, setBusinesses] = useState<Business[]>([]);
  // 초기값 true - 로딩 끝나기 전 등록 화면으로 잘못 튕기는 것 방지
  const [hasAnyBusiness, setHasAnyBusiness] = useState(true);
  const [hasPendingReview, setHasPendingReview] = useState(true);
  const [boothSlots, setBoothSlots] = useState<BoothSlotLockStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [selectedSlotIds, setSelectedSlotIds] = useState<number[]>([]);
  const [form, setForm] = useState<FormState>(initialForm);
  const [attachmentObjectKey, setAttachmentObjectKey] = useState<string | null>(null);
  const [attachmentUploading, setAttachmentUploading] = useState(false);

  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [result, setResult] = useState<ApplicationResponse | null>(null);

  const errorsRef = useRef<HTMLDivElement>(null);

  // 에러가 새로 생기면(신청하기 눌렀는데 검증 실패) 에러 박스로 스크롤해서
  // 사용자가 폼 하단(제출 버튼 근처)에 있어도 에러를 놓치지 않게 한다.
  useEffect(() => {
    if (errors.length > 0) {
      errorsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [errors]);

  useEffect(() => {
    if (!fairId) return;
    let ignore = false;

    setLoading(true);
    setLoadError(null);
    Promise.all([getBoothSlots(Number(fairId)), getMyBusinesses()])
      .then(([slots, myBusinesses]) => {
        if (ignore) return;
        setBoothSlots(slots);
        setHasAnyBusiness(myBusinesses.length > 0);
        setHasPendingReview(myBusinesses.some((business) => business.approvalStatus === "PENDING_REVIEW"));
        // 승인된 사업자만 신청 가능 - 심사대기/반려/취소된 사업자는 목록/셀렉트에서 제외
        setBusinesses(myBusinesses.filter((business) => business.approvalStatus === "APPROVED"));
      })
      .catch((error) => {
        if (ignore) return;
        if (error instanceof ApiError && error.status === 401) {
          setLoadError("LOGIN_REQUIRED");
        } else {
          setLoadError(error instanceof ApiError ? error.message : "신청 정보를 불러오지 못했어요.");
        }
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [fairId]);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }

  function toggleSlot(boothSlotId: number) {
    setSelectedSlotIds((previous) => {
      if (previous.includes(boothSlotId)) {
        return previous.filter((id) => id !== boothSlotId);
      }
      if (previous.length >= MAX_SLOTS) {
        return previous;
      }
      return [...previous, boothSlotId];
    });
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationErrors = validate(form, selectedSlotIds);
    if (attachmentUploading) validationErrors.push("첨부파일 업로드가 끝날 때까지 잠시만 기다려 주세요.");
    setErrors(validationErrors);
    if (validationErrors.length > 0) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      const response = await submitApplication(Number(fairId), toRequest(form, selectedSlotIds, attachmentObjectKey));
      setResult(response);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "신청서를 제출하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <PageContainer className="py-10"><p className="text-sm text-muted">불러오는 중...</p></PageContainer>;
  }

  if (loadError) {
    return (
      <PageContainer className="py-10">
        {loadError === "LOGIN_REQUIRED" ? (
          <EmptyState
            title="로그인이 필요해요"
            description="참가 신청을 하려면 먼저 로그인해 주세요."
            actionTo="/login"
            actionLabel="로그인하러 가기"
          />
        ) : (
          <EmptyState title="신청 정보를 불러오지 못했어요" description={loadError} />
        )}
      </PageContainer>
    );
  }

  // 사업자 자체가 하나도 없으면 등록 화면으로 보낸다. from을 함께 넘겨,
  // 사업자 등록을 마치면 BusinessRegisterPage가 이 신청 화면으로 되돌려보낸다.
  if (!hasAnyBusiness) {
    return <Navigate to="/businesses/new" replace state={{ from: `/fairs/${fairId}/apply` }} />;
  }

  // 사업자는 있지만 전부 심사 대기중이거나 반려/취소된 상태 - 등록 화면으로 또 보내면
  // "방금 등록했는데 왜 또 등록하라는 거지" 하는 혼란스러운 루프가 생기니, 안내만 보여준다.
  if (businesses.length === 0) {
    return (
      <PageContainer className="py-10">
        <EmptyState
          title={hasPendingReview ? "승인된 사업자가 없어요" : "사업자 등록이 반려·취소됐어요"}
          description={
            hasPendingReview
              ? "사업자 등록 심사가 완료되면 참가 신청을 할 수 있어요."
              : "등록한 사업자가 반려되거나 취소됐어요. 사유를 확인하고 다시 등록해 주세요."
          }
          actionTo="/businesses/me"
          actionLabel="내 사업자 확인하기"
        />
      </PageContainer>
    );
  }

  if (result) {
    return (
      <PageContainer className="py-10">
        <div className="surface mx-auto max-w-lg p-8 text-center">
          <div className="mx-auto mb-4 grid size-14 place-items-center rounded-full bg-leaf-soft text-ink">
            <CheckCircle2 size={26} />
          </div>
          <h1 className="text-xl font-extrabold">신청서가 접수되었어요.</h1>
          <p className="mt-2 text-sm leading-6 text-muted">
            담당자 심사 후 결과를 안내드릴게요. 내 신청 현황에서 진행 상태를 확인할 수 있어요.
          </p>
          <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-center">
            <Link to="/participations/me" className="inline-flex min-h-11 items-center justify-center rounded-button border border-line bg-card px-4 text-sm font-bold hover:bg-page">내 신청 현황</Link>
            <Link to="/" className="inline-flex min-h-11 items-center justify-center rounded-button bg-primary-strong px-4 text-sm font-bold text-white hover:opacity-90">홈으로 이동</Link>
          </div>
        </div>
      </PageContainer>
    );
  }

  const hallGroups = groupByHall(boothSlots);
  // 선택 순서 그대로(selectedSlotIds 배열 순서 = 클릭한 순서) 슬롯 번호로 변환해서 보여준다.
  const selectedSlotLabels = selectedSlotIds
    .map((id) => boothSlots.find((slot) => slot.boothSlotsId === id)?.slotNumber)
    .filter((slotNumber): slotNumber is string => Boolean(slotNumber));

  return (
    <PageContainer className="py-10">
      <PageHeader
        eyebrow="참가업체 모집"
        title="참가 신청서 작성"
        description="부스 슬롯을 최대 3개까지 선택하고, 신청 정보를 입력해 주세요. *는 필수 입력이에요."
      />

      {errors.length > 0 && (
  <div ref={errorsRef} className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong scroll-mt-[88px]">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <ul className="space-y-1">
            {errors.map((message) => <li key={message}>{message}</li>)}
          </ul>
        </div>
      )}

      {submitError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{submitError}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-10">
        <section>
          <SectionHeader title="부스 슬롯 선택" description={`선택된 슬롯: ${selectedSlotIds.length} / ${MAX_SLOTS}`} />
          {hallGroups.length === 0 ? (
            <EmptyState title="선택 가능한 부스 슬롯이 없어요" />
          ) : (
            <div className="space-y-6">
              {hallGroups.map((group) => (
                <HallBoothMap
                  key={group.hallId}
                  hallName={group.hallName}
                  backgroundImageUrl={group.floorPlanImageUrl}
                  onSlotClick={toggleSlot}
                  slots={group.slots.map((slot) => ({
                    boothSlotsId: slot.boothSlotsId,
                    slotNumber: slot.slotNumber,
                    posX: slot.posX,
                    posY: slot.posY,
                    width: slot.width,
                    height: slot.height,
                    caption: `${slot.price.toLocaleString()}원`,
                    tone: slot.locked ? "neutral" : "leaf",
                    locked: slot.locked,
                    selected: selectedSlotIds.includes(slot.boothSlotsId),
                  }))}
                />
              ))}
            </div>
          )}
          <p className="mt-3 text-sm font-bold text-ink">
            선택한 슬롯: {selectedSlotLabels.length > 0 ? selectedSlotLabels.join(", ") : "없음"}
          </p>
        </section>

        <section>
          <SectionHeader title="신청 사업자" />
          <Card className="space-y-5 p-6">
            <div>
              {label("businessId", "사업자 선택", true)}
              <Select id="businessId" value={form.businessId} onChange={(event) => update("businessId", event.target.value)} required>
                <option value="">선택해 주세요</option>
                {businesses.map((business) => (
                  <option key={business.businessId} value={business.businessId}>{business.name}</option>
                ))}
              </Select>
            </div>
          </Card>
        </section>

        <section>
          <SectionHeader title="신청 내용" description="심사에 참고할 참가 목적과 판매·전시 품목이에요." />
          <Card className="space-y-5 p-6">
            <div>
              {label("purpose", "참가 목적", true)}
              <Textarea id="purpose" value={form.purpose} onChange={(event) => update("purpose", event.target.value)} placeholder="이 행사에 참가하려는 목적을 입력해 주세요." maxLength={200} />
            </div>
            <div>
              {label("itemsDesc", "판매·전시 품목", true)}
              <Textarea id="itemsDesc" value={form.itemsDesc} onChange={(event) => update("itemsDesc", event.target.value)} placeholder="부스에서 판매하거나 전시할 품목을 입력해 주세요." maxLength={500} />
            </div>
            <AttachmentUploadField
              label="첨부파일 (선택)"
              onObjectKeyChange={setAttachmentObjectKey}
              onUploadingChange={setAttachmentUploading}
            />
          </Card>
        </section>

        <section>
          <SectionHeader title="담당자 정보" description="심사 결과와 결제 안내를 받을 연락처예요." />
          <Card className="space-y-5 p-6">
            <div className="grid gap-5 sm:grid-cols-3">
              <div>
                {label("managerName", "담당자 이름", true)}
                <Input id="managerName" value={form.managerName} onChange={(event) => update("managerName", event.target.value)} maxLength={50} required />
              </div>
              <div>
                {label("managerPhone", "담당자 연락처", true)}
                <Input
                  id="managerPhone"
                  type="tel"
                  value={form.managerPhone}
                  onChange={(event) => update("managerPhone", event.target.value)}
                  placeholder="010-0000-0000"
                  pattern="\d{2,3}-\d{3,4}-\d{4}"
                  maxLength={20}
                  required
                />
              </div>
              <div>
                {label("managerEmail", "담당자 이메일", true)}
                <Input id="managerEmail" type="email" value={form.managerEmail} onChange={(event) => update("managerEmail", event.target.value)} maxLength={100} required />
              </div>
            </div>
          </Card>
        </section>

        <label className="flex items-start gap-2 text-sm text-ink">
          <input
            type="checkbox"
            checked={form.agreedTerms}
            onChange={(event) => update("agreedTerms", event.target.checked)}
            className="mt-0.5"
          />
          <span>이용약관 및 참가 신청 유의사항에 동의합니다. <span className="text-primary-strong">*</span></span>
        </label>

        <div className="flex justify-end">
          <Button type="submit" disabled={submitting || attachmentUploading}>
            <Send size={16} />
            {submitting ? "제출 중..." : attachmentUploading ? "파일 업로드 중..." : "신청서 제출"}
          </Button>
        </div>
      </form>
    </PageContainer>
  );
}