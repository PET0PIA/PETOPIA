import { AlertCircle, CalendarDays, FileText, MapPin, Save, Ticket, UserRound } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { PageContainer } from "../../components/common/PageContainer";
import { PageHeader } from "../../components/common/PageHeader";
import { SectionHeader } from "../../components/common/SectionHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { ImageUploadField } from "../../components/ui/ImageUploadField";
import { Select } from "../../components/ui/Select";
import { Textarea } from "../../components/ui/Textarea";
import { HelpTip } from "../../components/ui/HelpTip";
import { ApiError } from "../../api/client";
import {
  getMyApplicationDetail,
  updateFairApplication,
  type FairApplicationDetail,
  type UpdateFairApplicationRequest,
} from "../../api/fair";
import { getMe } from "../../api/user";

// 신청서 수정(재제출)은 백엔드가 RECEIVED(심사 대기)/REJECTED(반려) 상태에서만 허용한다
// (FairService#updateApplication 참고) - 최종 판단은 항상 백엔드가 하지만, 화면에서도 미리
// 걸러서 어차피 거부될 폼을 보여주지 않는다.
const EDITABLE_STATUSES = new Set(["RECEIVED", "REJECTED"]);

interface FormState {
  name: string;
  description: string;
  category: "" | "DOG" | "CAT" | "ETC";
  /** 반려동물 동반 가능 여부. 서버가 NOT NULL이라 "선택 안 함"이 없다. */
  petAllowed: "true" | "false";
  noticeText: string;
  placeName: string;
  address: string;
  indoorOutdoor: "" | "INDOOR" | "OUTDOOR";
  vendorRecruitStartDate: string;
  vendorRecruitEndDate: string;
  reservationStartDate: string;
  reservationEndDate: string;
  operationStartDate: string;
  operationEndDate: string;
  reservationFee: string;
  reservationCancelDeadlineHours: string;
  reservationChangeDeadlineHours: string;
  managerName: string;
  managerPhone: string;
  managerEmail: string;
}

function formStateFromDetail(detail: FairApplicationDetail): FormState {
  return {
    name: detail.name,
    description: detail.description ?? "",
    category: detail.category ?? "",
    petAllowed: detail.petAllowed ? "true" : "false",
    noticeText: detail.noticeText ?? "",
    placeName: detail.placeName ?? "",
    address: detail.address ?? "",
    indoorOutdoor: detail.indoorOutdoor ?? "",
    vendorRecruitStartDate: detail.vendorRecruitStartDate ?? "",
    vendorRecruitEndDate: detail.vendorRecruitEndDate ?? "",
    reservationStartDate: detail.reservationStartDate ?? "",
    reservationEndDate: detail.reservationEndDate ?? "",
    operationStartDate: detail.operationStartDate ?? "",
    operationEndDate: detail.operationEndDate ?? "",
    reservationFee: detail.reservationFee !== null ? String(detail.reservationFee) : "",
    reservationCancelDeadlineHours: detail.reservationCancelDeadlineHours !== null ? String(detail.reservationCancelDeadlineHours) : "",
    reservationChangeDeadlineHours: detail.reservationChangeDeadlineHours !== null ? String(detail.reservationChangeDeadlineHours) : "",
    managerName: detail.managerName,
    managerPhone: detail.managerPhone ?? "",
    managerEmail: detail.managerEmail,
  };
}

function label(htmlFor: string, text: string, required = false, help?: string) {
  return (
    <div className="mb-1.5 flex items-center gap-1">
      <label htmlFor={htmlFor} className="text-sm font-bold text-ink">{text}{required && <span className="ml-1 text-primary-strong">*</span>}</label>
      {help && <HelpTip text={help} />}
    </div>
  );
}

/** 일정의 한 기간: 제목 + [시작] ~ [종료]를 한 줄로. 날짜칸을 넉넉히 두고 반복 라벨을 없애 읽기 쉽게 한다. */
function PeriodFields({ title, startId, startValue, endId, endValue, onStart, onEnd }: {
  title: string;
  startId: string;
  startValue: string;
  endId: string;
  endValue: string;
  onStart: (value: string) => void;
  onEnd: (value: string) => void;
}) {
  return (
    <div>
      <p className="mb-1.5 text-sm font-bold text-ink">{title}</p>
      <div className="flex max-w-md items-center gap-3">
        <Input id={startId} type="date" aria-label={`${title} 시작일`} value={startValue} onChange={(event) => onStart(event.target.value)} className="min-w-0 flex-1" />
        <span className="shrink-0 text-sm text-muted" aria-hidden="true">~</span>
        <Input id={endId} type="date" aria-label={`${title} 종료일`} value={endValue} onChange={(event) => onEnd(event.target.value)} className="min-w-0 flex-1" />
      </div>
    </div>
  );
}

// FairApplicationNewPage.tsx/EditProfilePage.tsx의 PHONE_PATTERN과 동일 - 이 프로젝트의 휴대폰 번호 형식 검증 관례.
const PHONE_PATTERN = /^01[0-9]-?\d{3,4}-?\d{4}$/;

function validate(form: FormState): string[] {
  const errors: string[] = [];
  if (form.name.trim() === "") errors.push("행사명을 입력해 주세요.");
  if (form.managerName.trim() === "") errors.push("담당자 이름을 입력해 주세요.");
  if (form.managerEmail.trim() === "") errors.push("담당자 이메일을 입력해 주세요.");
  if (form.managerPhone.trim() !== "" && !PHONE_PATTERN.test(form.managerPhone.trim())) {
    errors.push("담당자 연락처 형식이 올바르지 않아요. (예: 010-1234-5678)");
  }
  if (form.reservationFee !== "" && Number(form.reservationFee) < 0) errors.push("예약금은 0 이상이어야 해요.");

  const periods: Array<[string, string, string]> = [
    [form.vendorRecruitStartDate, form.vendorRecruitEndDate, "참가업체 모집 기간"],
    [form.reservationStartDate, form.reservationEndDate, "예약 기간"],
    [form.operationStartDate, form.operationEndDate, "운영 기간"],
  ];
  for (const [start, end, periodLabel] of periods) {
    if (start !== "" && end !== "" && end < start) {
      errors.push(`${periodLabel}의 종료일이 시작일보다 빠를 수 없어요.`);
    }
  }
  return errors;
}

/**
 * 폼은 항상 기존 신청서 전체를 불러와 채운 상태로 시작하므로(formStateFromDetail), 여기서는
 * "생략"(undefined) 없이 화면에 보이는 값을 그대로(비어 있으면 null로) 보낸다 - 사용자가 필드를
 * 지우고 제출하면 실제로 지워져야 하기 때문이다("PATCH 계약" 참고). posterImageObjectKey만
 * 예외로, 새로 업로드하지도 삭제하지도 않았으면 undefined를 보내 기존 포스터를 그대로 둔다.
 */
function toRequest(form: FormState, uploadedPosterKey: string | null, posterRemoved: boolean): UpdateFairApplicationRequest {
  return {
    name: form.name.trim(),
    description: form.description.trim() || null,
    category: form.category || null,
    posterImageObjectKey: uploadedPosterKey ?? (posterRemoved ? null : undefined),
    noticeText: form.noticeText.trim() || null,
    petAllowed: form.petAllowed === "true",
    placeName: form.placeName.trim() || null,
    address: form.address.trim() || null,
    indoorOutdoor: form.indoorOutdoor || null,
    vendorRecruitStartDate: form.vendorRecruitStartDate || null,
    vendorRecruitEndDate: form.vendorRecruitEndDate || null,
    reservationStartDate: form.reservationStartDate || null,
    reservationEndDate: form.reservationEndDate || null,
    operationStartDate: form.operationStartDate || null,
    operationEndDate: form.operationEndDate || null,
    reservationFee: form.reservationFee === "" ? null : Number(form.reservationFee),
    reservationCancelDeadlineHours: form.reservationCancelDeadlineHours === "" ? null : Number(form.reservationCancelDeadlineHours),
    reservationChangeDeadlineHours: form.reservationChangeDeadlineHours === "" ? null : Number(form.reservationChangeDeadlineHours),
    managerName: form.managerName.trim(),
    managerPhone: form.managerPhone.trim() || null,
    managerEmail: form.managerEmail.trim(),
  };
}

export function FairApplicationEditPage() {
  const { fairId } = useParams<{ fairId: string }>();
  const id = Number(fairId);
  const idValid = Number.isInteger(id) && id > 0;

  if (!idValid) {
    return (
      <PageContainer className="py-10">
        <EmptyState
          title="잘못된 신청서 주소예요."
          description="신청서 주소가 올바르지 않아요. 목록에서 다시 선택해 주세요."
          actionTo="/fair-applications/me"
          actionLabel="내 신청 현황으로"
        />
      </PageContainer>
    );
  }

  // id별 key로 리마운트해서, 다른 신청서 수정 화면으로 바로 이동해도 이전 신청서의
  // 폼 상태가 잔류하지 않게 한다(MyFairApplicationDetailPage와 동일한 패턴).
  return <FairApplicationEditContent key={id} id={id} />;
}

function FairApplicationEditContent({ id }: { id: number }) {
  const navigate = useNavigate();

  const [detail, setDetail] = useState<FairApplicationDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [form, setForm] = useState<FormState | null>(null);
  const [posterImageObjectKey, setPosterImageObjectKey] = useState<string | null>(null);
  const [posterImageUploading, setPosterImageUploading] = useState(false);
  const [posterRemoved, setPosterRemoved] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const errorsRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let alive = true;
    Promise.all([getMyApplicationDetail(id), getMe()])
      .then(([res, me]) => {
        if (!alive) return;
        setDetail(res);
        setForm({ ...formStateFromDetail(res), managerEmail: me.email });
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setLoadError(err instanceof ApiError ? err.message : "신청서를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [id]);

  // 폼이 길어, 하단 제출 버튼에서 눌렀을 때 위쪽 오류를 놓칠 수 있다.
  // 검증 오류나 서버 오류가 생기면 오류 박스로 스크롤해 준다(신청 화면과 동일).
  useEffect(() => {
    if (errors.length > 0 || submitError) {
      errorsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [errors, submitError]);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((previous) => (previous ? { ...previous, [key]: value } : previous));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form) return;
    const validationErrors = validate(form);
    if (posterImageUploading) validationErrors.push("포스터 이미지 업로드가 끝날 때까지 잠시만 기다려 주세요.");
    setErrors(validationErrors);
    if (validationErrors.length > 0) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      await updateFairApplication(id, toRequest(form, posterImageObjectKey, posterRemoved));
      navigate(`/fair-applications/me/${id}`);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "신청서를 수정하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <PageContainer className="py-10">
        <p className="py-16 text-center text-sm text-muted">신청서를 불러오는 중이에요…</p>
      </PageContainer>
    );
  }

  if (loadError || !detail || !form) {
    return (
      <PageContainer className="py-10">
        <EmptyState
          title="신청서를 찾을 수 없어요."
          description={loadError ?? "목록에서 신청서를 다시 선택해 주세요."}
          actionTo="/fair-applications/me"
          actionLabel="내 신청 현황으로"
        />
      </PageContainer>
    );
  }

  if (detail.canceledAt || !EDITABLE_STATUSES.has(detail.status)) {
    return (
      <PageContainer className="py-10">
        <EmptyState
          title="지금은 이 신청서를 수정할 수 없어요."
          description="심사 대기 중이거나 반려된 신청서만 수정할 수 있어요."
          actionTo={`/fair-applications/me/${id}`}
          actionLabel="신청서 상세로"
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer className="py-10">
     <div className="mx-auto max-w-4xl">
      <PageHeader
        eyebrow="마이페이지"
        title="신청서 수정"
        description={
          detail.status === "REJECTED"
            ? "반려된 신청서예요. 내용을 고쳐서 다시 제출하면 심사 대기 상태로 돌아가요. *는 필수 입력이에요."
            : "심사 대기 중인 신청서예요. 내용을 고쳐서 다시 제출할 수 있어요. *는 필수 입력이에요."
        }
      />

      {detail.status === "REJECTED" && detail.rejectReason && (
        <div role="alert" className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} aria-hidden="true" className="mt-0.5 shrink-0" />
          <p>반려 사유: {detail.rejectReason}</p>
        </div>
      )}

      {/* 스크롤 목표 지점. 검증/서버 오류가 뜨면 위 useEffect가 이 영역으로 데려온다. */}
      <div ref={errorsRef} className="scroll-mt-24">
        {errors.length > 0 && (
          <div role="alert" className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
            <AlertCircle size={18} aria-hidden="true" className="mt-0.5 shrink-0" />
            <ul className="space-y-1">
              {errors.map((message) => <li key={message}>{message}</li>)}
            </ul>
          </div>
        )}

        {submitError && (
          <div role="alert" className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
            <AlertCircle size={18} aria-hidden="true" className="mt-0.5 shrink-0" />
            <p>{submitError}</p>
          </div>
        )}
      </div>

      <form onSubmit={handleSubmit} className="space-y-6">
        <section>
          <SectionHeader title="기본 정보" help="행사를 소개하는 내용이에요." icon={<FileText size={18} aria-hidden="true" />} />
          <Card className="p-6">
            <div className="flex flex-col gap-6 sm:flex-row sm:gap-8">
              {/* 포스터를 크게 왼쪽에 두고, 오른쪽 입력칸은 짧게 묶어 높이를 맞춘다. */}
              <div className="shrink-0">
                <ImageUploadField
                  label="포스터 이미지"
                  previewClassName="aspect-[4/5] w-56"
                  layout="stacked"
                  initialImageUrl={detail.posterImageUrl}
                  onObjectKeyChange={(key) => {
                    setPosterImageObjectKey(key);
                    if (key) setPosterRemoved(false);
                  }}
                  onUploadingChange={setPosterImageUploading}
                  removable
                  onRemove={() => {
                    setPosterImageObjectKey(null);
                    setPosterRemoved(true);
                  }}
                />
              </div>
              <div className="flex-1 space-y-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    {label("name", "행사명", true)}
                    <Input id="name" value={form.name} onChange={(event) => update("name", event.target.value)} placeholder="예: 2026 서울 펫페어" required />
                  </div>
                  <div>
                    {label("category", "카테고리")}
                    <Select id="category" value={form.category} onChange={(event) => update("category", event.target.value as FormState["category"])}>
                      <option value="">선택 안 함</option>
                      <option value="DOG">강아지</option>
                      <option value="CAT">고양이</option>
                      <option value="ETC">기타</option>
                    </Select>
                  </div>
                </div>
                <div>
                  {label("description", "행사 소개")}
                  <Textarea id="description" value={form.description} onChange={(event) => update("description", event.target.value)} placeholder="행사를 소개해 주세요." />
                </div>
                <div>
                  {label("petAllowed", "반려동물 동반")}
                  <Select id="petAllowed" value={form.petAllowed} onChange={(event) => update("petAllowed", event.target.value as FormState["petAllowed"])}>
                    <option value="true">동반 가능</option>
                    <option value="false">동반 금지</option>
                  </Select>
                  <p className="mt-1.5 text-xs text-muted">동반 금지로 두면 관람객이 예약할 때 반려동물을 선택할 수 없어요.</p>
                </div>
                <div>
                  {label("noticeText", "관람 안내사항")}
                  <Textarea id="noticeText" value={form.noticeText} onChange={(event) => update("noticeText", event.target.value)} placeholder="방문객이 꼭 알아야 할 관람 안내사항을 입력해 주세요." />
                </div>
              </div>
            </div>
          </Card>
        </section>

        <section>
          <SectionHeader title="장소" help="행사가 열리는 장소 정보예요." icon={<MapPin size={18} aria-hidden="true" />} />
          <Card className="space-y-5 p-6">
            <div className="grid gap-5 sm:grid-cols-2">
              <div>
                {label("placeName", "장소명")}
                <Input id="placeName" value={form.placeName} onChange={(event) => update("placeName", event.target.value)} placeholder="예: 서울 코엑스 C홀" />
              </div>
              <div>
                {label("indoorOutdoor", "실내/실외")}
                <Select id="indoorOutdoor" value={form.indoorOutdoor} onChange={(event) => update("indoorOutdoor", event.target.value as FormState["indoorOutdoor"])}>
                  <option value="">선택 안 함</option>
                  <option value="INDOOR">실내</option>
                  <option value="OUTDOOR">실외</option>
                </Select>
              </div>
            </div>
            <div>
              {label("address", "주소")}
              <Input id="address" value={form.address} onChange={(event) => update("address", event.target.value)} placeholder="상세 주소를 입력해 주세요." />
            </div>
          </Card>
        </section>

        <section>
          <SectionHeader title="일정" help="아는 기간만 입력해도 돼요. 보통 참가업체 모집 → 관람객 사전예약 → 행사 운영 순서로 진행돼요." icon={<CalendarDays size={18} aria-hidden="true" />} />
          <Card className="space-y-5 p-6">
            <PeriodFields
              title="참가업체 모집"
              startId="vendorRecruitStartDate"
              startValue={form.vendorRecruitStartDate}
              endId="vendorRecruitEndDate"
              endValue={form.vendorRecruitEndDate}
              onStart={(value) => update("vendorRecruitStartDate", value)}
              onEnd={(value) => update("vendorRecruitEndDate", value)}
            />
            <PeriodFields
              title="관람객 사전예약"
              startId="reservationStartDate"
              startValue={form.reservationStartDate}
              endId="reservationEndDate"
              endValue={form.reservationEndDate}
              onStart={(value) => update("reservationStartDate", value)}
              onEnd={(value) => update("reservationEndDate", value)}
            />
            <PeriodFields
              title="행사 운영"
              startId="operationStartDate"
              startValue={form.operationStartDate}
              endId="operationEndDate"
              endValue={form.operationEndDate}
              onStart={(value) => update("operationStartDate", value)}
              onEnd={(value) => update("operationEndDate", value)}
            />
          </Card>
        </section>

        {/* 예약 정책·담당자는 둘 다 짧아 나란히 둔다(높이가 비슷해 깔끔). 좁은 화면에선 위아래로 쌓인다. */}
        <div className="grid gap-6 lg:grid-cols-2 lg:items-start">
          <section>
            <SectionHeader title="예약 정책" help="관람객 예약금과 취소·변경 가능 기한이에요." icon={<Ticket size={18} aria-hidden="true" />} />
            <Card className="space-y-5 p-6">
              <div>
                {label("reservationFee", "예약금(원)", false, "관람객이 예매 때 내는 예약금이에요. 무료면 0.")}
                <Input id="reservationFee" type="number" min={0} value={form.reservationFee} onChange={(event) => update("reservationFee", event.target.value)} placeholder="0" />
              </div>
              <div className="grid gap-5 sm:grid-cols-2">
                <div>
                  {label("reservationCancelDeadlineHours", "취소 가능 기한(시간)", false, "방문 몇 시간 전까지 취소를 허용할지. 예: 24 = 하루 전까지. 비워두면 기본 12시간.")}
                  <Input id="reservationCancelDeadlineHours" type="number" min={0} value={form.reservationCancelDeadlineHours} onChange={(event) => update("reservationCancelDeadlineHours", event.target.value)} placeholder="예: 24" />
                </div>
                <div>
                  {label("reservationChangeDeadlineHours", "변경 가능 기한(시간)", false, "방문 몇 시간 전까지 방문일 변경을 허용할지. 비워두면 기본 12시간.")}
                  <Input id="reservationChangeDeadlineHours" type="number" min={0} value={form.reservationChangeDeadlineHours} onChange={(event) => update("reservationChangeDeadlineHours", event.target.value)} placeholder="예: 24" />
                </div>
              </div>
            </Card>
          </section>

          <section>
            <SectionHeader title="담당자 정보" help="심사 결과와 개설비 결제 안내를 받을 연락처예요." icon={<UserRound size={18} aria-hidden="true" />} />
            <Card className="space-y-5 p-6">
              <div className="grid gap-5 sm:grid-cols-2">
                <div>
                  {label("managerName", "담당자 이름", true)}
                  <Input id="managerName" value={form.managerName} onChange={(event) => update("managerName", event.target.value)} required />
                </div>
                <div>
                  {label("managerPhone", "담당자 연락처")}
                  <Input id="managerPhone" value={form.managerPhone} onChange={(event) => update("managerPhone", event.target.value)} placeholder="010-0000-0000" />
                </div>
              </div>
              <div>
                {label("managerEmail", "담당자 이메일", true)}
                <Input id="managerEmail" type="email" value={form.managerEmail} readOnly required className="bg-page text-muted" />
                <p className="mt-1.5 text-xs text-muted">로그인한 계정 이메일은 변경할 수 없어요.</p>
              </div>
            </Card>
          </section>
        </div>

        <div className="flex flex-col gap-3 border-t border-line pt-6 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-xs leading-5 text-muted">
            {detail.status === "REJECTED"
              ? "수정해서 다시 제출하면 심사 대기 상태로 돌아가요."
              : "수정한 내용은 다시 제출해야 저장돼요."}
          </p>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            <Link
              to={`/fair-applications/me/${id}`}
              className="inline-flex min-h-11 items-center justify-center rounded-button border border-line bg-card px-4 text-sm font-bold hover:bg-page"
            >
              취소
            </Link>
            <Button type="submit" disabled={submitting || posterImageUploading} className="w-full sm:w-auto">
              <Save size={16} />
              {submitting ? "저장 중..." : posterImageUploading ? "이미지 업로드 중..." : "수정해서 다시 제출"}
            </Button>
          </div>
        </div>
      </form>
     </div>
    </PageContainer>
  );
}
