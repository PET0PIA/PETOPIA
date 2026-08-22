import { AlertCircle, AlertTriangle, CalendarRange, Info, Megaphone, Pencil, Plus, Trash2 } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import {
  createFairDate,
  deleteFairDate,
  getFairDates,
  getFairInfo,
  getFairPublishStatus,
  getReservationPeriod,
  publishFair,
  updateFairDate,
  updateFairInfo,
  updateReservationPeriod,
  type CreateFairDateRequest,
  type FairCategory,
  type FairDate,
  type IndoorOutdoor,
  type UpdateFairDateRequest,
  type UpdateFairInfoRequest,
} from "../../api/fair";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { ImageUploadField } from "../../components/ui/ImageUploadField";
import { Input } from "../../components/ui/Input";
import { Select } from "../../components/ui/Select";
import { Table } from "../../components/ui/Table";
import { Textarea } from "../../components/ui/Textarea";
import { useConfirm } from "../../components/ui/useConfirm";
import { useFairSelector } from "../../contexts/FairSelectorContext";

// FairApplicationNewPage.tsx/FairApplicationEditPage.tsx의 PHONE_PATTERN과 동일 - 이 프로젝트의
// 휴대폰 번호 형식 검증 관례.
const PHONE_PATTERN = /^01[0-9]-?\d{3,4}-?\d{4}$/;

interface FairInfoFormState {
  name: string;
  description: string;
  category: "" | FairCategory;
  noticeText: string;
  placeName: string;
  address: string;
  indoorOutdoor: "" | IndoorOutdoor;
  operationStartDate: string;
  operationEndDate: string;
  managerName: string;
  managerPhone: string;
}

function validateFairInfo(form: FairInfoFormState): string[] {
  const errors: string[] = [];
  if (form.name.trim() === "") errors.push("행사명을 입력해 주세요.");
  if (form.managerName.trim() === "") errors.push("담당자 이름을 입력해 주세요.");
  if (form.managerPhone.trim() !== "" && !PHONE_PATTERN.test(form.managerPhone.trim())) {
    errors.push("담당자 연락처 형식이 올바르지 않아요. (예: 010-1234-5678)");
  }
  if (form.operationStartDate !== "" && form.operationEndDate !== "" && form.operationEndDate < form.operationStartDate) {
    errors.push("운영 기간의 종료일이 시작일보다 빠를 수 없어요.");
  }
  return errors;
}

interface FairDateFormState {
  operationDate: string;
  capacity: string;
  entryStartTime: string;
  entryEndTime: string;
}

const emptyForm: FairDateFormState = { operationDate: "", capacity: "", entryStartTime: "", entryEndTime: "" };

function formatTime(time: string) {
  return time.slice(0, 5);
}

export function FairDateManagementPage() {
  const { confirm, confirmDialog } = useConfirm();

  // 콘솔 상단 바의 "관리 행사" 선택기가 현재 행사를 정한다(페이지 이동/새로고침에도 유지).
  const { fairId } = useFairSelector();

  // 포스터 업로드는 비동기라, 업로드 중에 다른 행사로 전환하면 ImageUploadField는
  // key={fairId}로 리마운트되지만 이미 시작된 업로드 프로미스는 취소되지 않는다. 완료 시
  // 예전 렌더의 콜백(그 시점의 fairId를 클로저로 들고 있음)이 그대로 호출되므로, 지금
  // 선택된 fairId와 다르면 무시해서 이전 행사의 objectKey가 지금 행사 state에 섞이는 걸 막는다.
  const currentFairIdRef = useRef(fairId);
  useEffect(() => {
    currentFairIdRef.current = fairId;
  }, [fairId]);

  const [fairDates, setFairDates] = useState<FairDate[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingFairDate, setEditingFairDate] = useState<FairDate | null>(null);
  const [form, setForm] = useState<FairDateFormState>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // 공개(publish) 버튼 - 진입 시 getFairPublishStatus로 지금 공개 상태를 미리 불러온다.
  // publishedAt이 채워지면(초기 조회든, 아래에서 직접 눌러 공개했든) 이미 공개된 것으로 보고
  // 버튼을 "공개됨"으로 바꾼다. 초기 조회가 실패해도(네트워크 등) 페이지를 막지 않고 조용히
  // 넘어간다 - 이 값은 버튼 상태를 미리 맞추는 용도일 뿐, 실제 공개 가능 여부는 눌렀을 때
  // 백엔드가 다시 검증한다(멱등이라 이미 공개된 행사를 다시 눌러도 최초 공개 결과를 그대로 돌려줌).
  const [publishedAt, setPublishedAt] = useState<string | null>(null);
  const [publishing, setPublishing] = useState(false);
  const [publishError, setPublishError] = useState<string | null>(null);

  // 사전예약 기간 - 진입 시 getReservationPeriod로 현재 값을 불러와 폼에 채워둔다.
  // 아직 설정 안 됐으면(null) 빈 문자열로 둬서 date input이 비어 보이게 한다.
  const [reservationStartDate, setReservationStartDate] = useState("");
  const [reservationEndDate, setReservationEndDate] = useState("");
  const [savingPeriod, setSavingPeriod] = useState(false);
  const [periodError, setPeriodError] = useState<string | null>(null);

  // 행사 정보(정보성 필드) - 진입 시 getFairInfo로 현재 값을 불러와 폼에 채워둔다. 승인·공개된
  // 뒤에도 이름/소개/카테고리/포스터/유의사항/장소/일정/담당자명·연락처를 여기서 고칠 수 있다.
  const [fairInfo, setFairInfo] = useState<FairInfoFormState | null>(null);
  const [posterImageUrl, setPosterImageUrl] = useState<string | null>(null);
  const [posterImageObjectKey, setPosterImageObjectKey] = useState<string | null>(null);
  const [posterImageUploading, setPosterImageUploading] = useState(false);
  const [posterRemoved, setPosterRemoved] = useState(false);
  const [infoErrors, setInfoErrors] = useState<string[]>([]);
  const [savingInfo, setSavingInfo] = useState(false);
  const [infoSubmitError, setInfoSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (fairId === null) return;
    let ignore = false;
    setLoading(true);
    setLoadError(null);
    setPublishedAt(null);
    setPublishError(null);
    setReservationStartDate("");
    setReservationEndDate("");
    setPeriodError(null);
    setFairInfo(null);
    setPosterImageUrl(null);
    setPosterImageObjectKey(null);
    setPosterRemoved(false);
    setInfoErrors([]);
    setInfoSubmitError(null);

    getFairDates(fairId)
      .then((data) => { if (!ignore) setFairDates(data); })
      .catch((error) => {
        if (!ignore) {
          // 조회 실패 시 이전 행사의 목록을 지운다 - 안 지우면 새 fairId 화면에 이전
          // 행사의 운영일이 그대로 남아 보인다(수정·삭제 버튼도 그 데이터를 대상으로 동작함).
          setFairDates([]);
          setLoadError(error instanceof ApiError ? error.message : "운영일 목록을 불러오지 못했어요.");
        }
      })
      .finally(() => { if (!ignore) setLoading(false); });

    getFairPublishStatus(fairId)
      .then((data) => { if (!ignore) setPublishedAt(data.publishedAt); })
      .catch(() => {});

    getReservationPeriod(fairId)
      .then((data) => {
        if (!ignore) {
          setReservationStartDate(data.reservationStartDate ?? "");
          setReservationEndDate(data.reservationEndDate ?? "");
        }
      })
      .catch(() => {});

    getFairInfo(fairId)
      .then((data) => {
        if (!ignore) {
          setFairInfo({
            name: data.name,
            description: data.description ?? "",
            category: data.category ?? "",
            noticeText: data.noticeText ?? "",
            placeName: data.placeName ?? "",
            address: data.address ?? "",
            indoorOutdoor: data.indoorOutdoor ?? "",
            operationStartDate: data.operationStartDate ?? "",
            operationEndDate: data.operationEndDate ?? "",
            managerName: data.managerName,
            managerPhone: data.managerPhone ?? "",
          });
          setPosterImageUrl(data.posterImageUrl);
        }
      })
      .catch(() => {});

    return () => { ignore = true; };
  }, [fairId]);

  function openCreateDialog() {
    setEditingFairDate(null);
    setForm(emptyForm);
    setFormError(null);
    setDialogOpen(true);
  }

  function openEditDialog(fairDate: FairDate) {
    setEditingFairDate(fairDate);
    setForm({
      operationDate: fairDate.operationDate,
      capacity: String(fairDate.capacity),
      entryStartTime: formatTime(fairDate.entryStartTime),
      entryEndTime: formatTime(fairDate.entryEndTime),
    });
    setFormError(null);
    setDialogOpen(true);
  }

  async function handleSaveFairDate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (fairId === null) return;

    const capacity = Number(form.capacity);
    if (!editingFairDate && form.operationDate === "") {
      setFormError("운영 날짜를 입력해 주세요.");
      return;
    }
    if (form.capacity === "" || !Number.isInteger(capacity) || capacity <= 0) {
      setFormError("정원은 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    if (form.entryStartTime === "" || form.entryEndTime === "") {
      setFormError("입장 시작·종료 시간을 입력해 주세요.");
      return;
    }
    if (form.entryStartTime >= form.entryEndTime) {
      setFormError("입장 종료 시간은 시작 시간보다 늦어야 해요.");
      return;
    }

    if (editingFairDate && capacity < editingFairDate.reservedCount) {
      // 백엔드(FairDateService#update)도 동일하게 막는다 - 이미 예약된 인원보다 정원을
      // 적게 줄이면 그 예약들이 근거를 잃는다. 여기서는 요청을 보내기 전에 먼저 막아
      // 불필요한 API 호출과 에러 메시지를 피한다.
      setFormError(`이미 ${editingFairDate.reservedCount}건 예약된 운영일이에요. 정원을 그보다 적게 줄일 수 없어요.`);
      return;
    }

    const proceed = await confirm({
      title: editingFairDate ? "운영일을 저장할까요?" : "운영일을 추가할까요?",
      description: editingFairDate
        ? "입력한 정원·입장 시간으로 저장할까요?"
        : "입력한 날짜·정원·입장 시간으로 새 운영일을 추가할까요?",
      confirmLabel: "저장",
    });
    if (!proceed) return;

    setSaving(true);
    setFormError(null);
    try {
      if (editingFairDate) {
        const payload: UpdateFairDateRequest = {
          capacity,
          entryStartTime: form.entryStartTime,
          entryEndTime: form.entryEndTime,
        };
        const saved = await updateFairDate(fairId, editingFairDate.fairDateId, payload);
        setFairDates((previous) => previous.map((item) => (item.fairDateId === saved.fairDateId ? saved : item)));
      } else {
        const payload: CreateFairDateRequest = {
          operationDate: form.operationDate,
          capacity,
          entryStartTime: form.entryStartTime,
          entryEndTime: form.entryEndTime,
        };
        const saved = await createFairDate(fairId, payload);
        setFairDates((previous) => [...previous, saved].sort((a, b) => a.operationDate.localeCompare(b.operationDate)));
      }
      setDialogOpen(false);
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : "운영일 정보를 저장하지 못했어요.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDeleteFairDate(fairDate: FairDate) {
    if (fairId === null) return;

    // 예약자가 있으면 백엔드(FairDateService#delete)가 무조건 거부한다 - 확인창을 띄워도
    // 소용없으니 여기서 바로 막는다.
    if (fairDate.reservedCount > 0) {
      setLoadError(`이미 예약이 ${fairDate.reservedCount}건 있는 운영일은 삭제할 수 없어요.`);
      return;
    }

    const description = fairDate.onsiteSalesConfigured
      ? `${fairDate.operationDate} 운영일은 현장예매 정책이 설정돼 있어요. 삭제하면 관련 데이터와 어긋날 수 있어요. 그래도 삭제할까요?`
      : `${fairDate.operationDate} 운영일을 삭제할까요?`;
    const proceed = await confirm({
      title: "운영일을 삭제할까요?",
      description,
      confirmLabel: "삭제",
      danger: fairDate.onsiteSalesConfigured,
    });
    if (!proceed) return;

    try {
      await deleteFairDate(fairId, fairDate.fairDateId);
      setFairDates((previous) => previous.filter((item) => item.fairDateId !== fairDate.fairDateId));
    } catch (error) {
      setLoadError(error instanceof ApiError ? error.message : "운영일을 삭제하지 못했어요.");
    }
  }

  async function handlePublish() {
    if (fairId === null) return;
    const proceed = await confirm({
      title: "행사를 공개할까요?",
      description: "공개하면 즉시 티켓 예매 화면에 노출되고 관람객 예약을 받을 수 있어요.",
      confirmLabel: "공개",
    });
    if (!proceed) return;

    setPublishing(true);
    setPublishError(null);
    try {
      const result = await publishFair(fairId);
      setPublishedAt(result.publishedAt);
    } catch (error) {
      setPublishError(error instanceof ApiError ? error.message : "공개 처리에 실패했어요.");
    } finally {
      setPublishing(false);
    }
  }

  async function handleSaveReservationPeriod(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (fairId === null) return;
    if (reservationStartDate === "" || reservationEndDate === "") {
      setPeriodError("사전예약 시작일과 종료일을 모두 입력해 주세요.");
      return;
    }
    if (reservationStartDate > reservationEndDate) {
      setPeriodError("종료일은 시작일보다 빠를 수 없어요.");
      return;
    }

    setSavingPeriod(true);
    setPeriodError(null);
    try {
      const result = await updateReservationPeriod(fairId, { reservationStartDate, reservationEndDate });
      setReservationStartDate(result.reservationStartDate ?? "");
      setReservationEndDate(result.reservationEndDate ?? "");
    } catch (error) {
      setPeriodError(error instanceof ApiError ? error.message : "사전예약 기간을 저장하지 못했어요.");
    } finally {
      setSavingPeriod(false);
    }
  }

  function updateFairInfoField<K extends keyof FairInfoFormState>(key: K, value: FairInfoFormState[K]) {
    setFairInfo((previous) => (previous ? { ...previous, [key]: value } : previous));
  }

  async function handleSaveFairInfo(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (fairId === null || !fairInfo) return;

    const validationErrors = validateFairInfo(fairInfo);
    if (posterImageUploading) validationErrors.push("포스터 이미지 업로드가 끝날 때까지 잠시만 기다려 주세요.");
    setInfoErrors(validationErrors);
    if (validationErrors.length > 0) return;

    setSavingInfo(true);
    setInfoSubmitError(null);
    try {
      const payload: UpdateFairInfoRequest = {
        name: fairInfo.name.trim(),
        description: fairInfo.description.trim() || null,
        category: fairInfo.category || null,
        posterImageObjectKey: posterImageObjectKey ?? (posterRemoved ? null : undefined),
        noticeText: fairInfo.noticeText.trim() || null,
        placeName: fairInfo.placeName.trim() || null,
        address: fairInfo.address.trim() || null,
        indoorOutdoor: fairInfo.indoorOutdoor || null,
        operationStartDate: fairInfo.operationStartDate || null,
        operationEndDate: fairInfo.operationEndDate || null,
        managerName: fairInfo.managerName.trim(),
        managerPhone: fairInfo.managerPhone.trim() || null,
      };
      const result = await updateFairInfo(fairId, payload);
      setFairInfo({
        name: result.name,
        description: result.description ?? "",
        category: result.category ?? "",
        noticeText: result.noticeText ?? "",
        placeName: result.placeName ?? "",
        address: result.address ?? "",
        indoorOutdoor: result.indoorOutdoor ?? "",
        operationStartDate: result.operationStartDate ?? "",
        operationEndDate: result.operationEndDate ?? "",
        managerName: result.managerName,
        managerPhone: result.managerPhone ?? "",
      });
      setPosterImageUrl(result.posterImageUrl);
      setPosterImageObjectKey(null);
      setPosterRemoved(false);
    } catch (error) {
      setInfoSubmitError(error instanceof ApiError ? error.message : "행사 정보를 저장하지 못했어요.");
    } finally {
      setSavingInfo(false);
    }
  }

  return (
    <div className="mx-auto max-w-6xl py-2">
      <PageHeader
        eyebrow="박람회 관리자"
        title="운영일·정원 관리"
        description="행사가 실제로 열리는 날짜별로 예약 정원과 입장 가능 시간을 관리해요."
        action={fairId !== null ? <Button onClick={openCreateDialog}><Plus size={16} />운영일 추가</Button> : undefined}
      />

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {fairId !== null && (
        <div className="surface mb-6 flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-3">
            <Megaphone size={18} className="mt-0.5 shrink-0 text-muted" />
            <div>
              <p className="text-sm font-bold text-ink">행사 공개</p>
              <p className="text-xs text-muted">
                {publishedAt
                  ? `공개됨 (${new Date(publishedAt).toLocaleString("ko-KR")})`
                  : "공개하면 관람객이 티켓 예매 화면에서 이 행사를 보고 예약할 수 있어요. 개설비 결제가 끝난 뒤에만 공개할 수 있어요."}
              </p>
              {publishError && <p className="mt-1 text-xs font-bold text-primary-strong">{publishError}</p>}
            </div>
          </div>
          <Button variant="outline" onClick={handlePublish} disabled={publishing || publishedAt !== null}>
            {publishing ? "공개 처리 중..." : publishedAt ? "공개됨" : "행사 공개하기"}
          </Button>
        </div>
      )}

      {fairId === null && (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 운영일 목록이 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
      )}

      {fairId !== null && loading && (
        <div className="surface grid min-h-40 place-items-center text-sm text-muted">운영일 목록을 불러오는 중이에요...</div>
      )}

      {fairId !== null && !loading && fairDates.length === 0 && !loadError && (
        <EmptyState title="등록된 운영일이 없어요." description="운영일 추가 버튼을 눌러 첫 운영일을 등록해 보세요." />
      )}

      {fairId !== null && !loading && fairDates.length > 0 && (
        <Table>
          <thead>
            <tr className="border-b border-line text-xs font-bold text-muted">
              <th className="px-4 py-3">운영 날짜</th>
              <th className="px-4 py-3">정원</th>
              <th className="px-4 py-3">입장 가능 시간</th>
              <th className="px-4 py-3">예약 현황</th>
              <th className="px-4 py-3">현장예매</th>
              <th className="px-4 py-3 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {fairDates.map((fairDate) => (
              <tr key={fairDate.fairDateId} className="border-b border-line last:border-0">
                <td className="px-4 py-3 font-bold">{fairDate.operationDate}</td>
                <td className="px-4 py-3 text-muted">{fairDate.capacity}명</td>
                <td className="px-4 py-3 text-muted">{formatTime(fairDate.entryStartTime)} ~ {formatTime(fairDate.entryEndTime)}</td>
                <td className="px-4 py-3 text-muted">
                  <span className={fairDate.reservedCount > fairDate.capacity ? "font-bold text-primary-strong" : undefined}>
                    {fairDate.reservedCount} / {fairDate.capacity}
                  </span>
                </td>
                <td className="px-4 py-3 text-muted">{fairDate.onsiteSalesConfigured ? "설정됨" : "-"}</td>
                <td className="px-4 py-3">
                  <div className="flex justify-end gap-2">
                    <button type="button" aria-label={`${fairDate.operationDate} 수정`} className="rounded-button p-2 text-muted hover:bg-page hover:text-ink" onClick={() => openEditDialog(fairDate)}>
                      <Pencil size={16} />
                    </button>
                    <button
                      type="button"
                      aria-label={`${fairDate.operationDate} 삭제`}
                      title={fairDate.reservedCount > 0 ? "예약자가 있는 운영일은 삭제할 수 없어요." : undefined}
                      disabled={fairDate.reservedCount > 0}
                      className="rounded-button p-2 text-muted hover:bg-page hover:text-primary-strong disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-muted"
                      onClick={() => handleDeleteFairDate(fairDate)}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      {fairId !== null && !loading && (
        <div className="surface mt-6 p-6">
          <div className="mb-4 flex items-start gap-3">
            <CalendarRange size={18} className="mt-0.5 shrink-0 text-muted" />
            <div>
              <h3 className="text-sm font-extrabold text-ink">사전예약 기간</h3>
              <p className="mt-1 text-xs text-muted">
                관람객이 티켓을 사전예약할 수 있는 기간이에요. 운영일 당일이어도 이 기간 안이면서
                그 날 입장 가능 시간 안이면 당일 사전예약도 받을 수 있어요.
              </p>
            </div>
          </div>
          <form onSubmit={handleSaveReservationPeriod} className="flex flex-col gap-4 sm:flex-row sm:items-end">
            <div className="flex-1">
              <label htmlFor="reservationStartDate" className="mb-1.5 block text-sm font-bold text-ink">
                시작일<span className="ml-1 text-primary-strong">*</span>
              </label>
              <Input
                id="reservationStartDate"
                type="date"
                value={reservationStartDate}
                onChange={(event) => setReservationStartDate(event.target.value)}
                required
              />
            </div>
            <div className="flex-1">
              <label htmlFor="reservationEndDate" className="mb-1.5 block text-sm font-bold text-ink">
                종료일<span className="ml-1 text-primary-strong">*</span>
              </label>
              <Input
                id="reservationEndDate"
                type="date"
                value={reservationEndDate}
                onChange={(event) => setReservationEndDate(event.target.value)}
                required
              />
            </div>
            <Button type="submit" disabled={savingPeriod}>{savingPeriod ? "저장 중..." : "저장"}</Button>
          </form>
          {periodError && <p className="mt-2 text-sm font-bold text-primary-strong">{periodError}</p>}
        </div>
      )}

      {fairId !== null && !loading && fairInfo && (
        <div className="surface mt-6 p-6">
          <div className="mb-4 flex items-start gap-3">
            <Info size={18} className="mt-0.5 shrink-0 text-muted" />
            <div>
              <h3 className="text-sm font-extrabold text-ink">행사 정보</h3>
              <p className="mt-1 text-xs text-muted">
                승인·공개된 뒤에도 이름·소개·포스터·장소·일정처럼 신청 당시 비워뒀거나 잘못 적은
                정보성 항목을 여기서 고칠 수 있어요. 예약금·기한·모집/예약 기간은 이미 진행 중인
                예약·모집에 영향을 줄 수 있어 이 화면에서 다루지 않아요.
              </p>
            </div>
          </div>

          <form onSubmit={handleSaveFairInfo} className="space-y-5">
            {infoErrors.length > 0 && (
              <ul className="list-inside list-disc space-y-1 text-sm font-bold text-primary-strong">
                {infoErrors.map((error) => <li key={error}>{error}</li>)}
              </ul>
            )}
            {infoSubmitError && <p className="text-sm font-bold text-primary-strong">{infoSubmitError}</p>}

            <div className="flex flex-col gap-6 sm:flex-row sm:gap-8">
              <div className="shrink-0">
                <ImageUploadField
                  key={fairId}
                  label="포스터 이미지"
                  previewClassName="aspect-[4/5] w-40"
                  layout="stacked"
                  initialImageUrl={posterImageUrl}
                  onObjectKeyChange={(key) => {
                    if (currentFairIdRef.current !== fairId) return; // 이전 행사에서 시작된 업로드가 뒤늦게 끝난 경우
                    setPosterImageObjectKey(key);
                    if (key) setPosterRemoved(false);
                  }}
                  onUploadingChange={setPosterImageUploading}
                  removable
                  onRemove={() => {
                    if (currentFairIdRef.current !== fairId) return;
                    setPosterImageObjectKey(null);
                    setPosterRemoved(true);
                  }}
                />
              </div>
              <div className="flex-1 space-y-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <label htmlFor="infoName" className="mb-1.5 block text-sm font-bold text-ink">행사명<span className="ml-1 text-primary-strong">*</span></label>
                    <Input id="infoName" value={fairInfo.name} onChange={(event) => updateFairInfoField("name", event.target.value)} required />
                  </div>
                  <div>
                    <label htmlFor="infoCategory" className="mb-1.5 block text-sm font-bold text-ink">카테고리</label>
                    <Select id="infoCategory" value={fairInfo.category} onChange={(event) => updateFairInfoField("category", event.target.value as FairInfoFormState["category"])}>
                      <option value="">선택 안 함</option>
                      <option value="DOG">강아지</option>
                      <option value="CAT">고양이</option>
                      <option value="ETC">기타</option>
                    </Select>
                  </div>
                </div>
                <div>
                  <label htmlFor="infoDescription" className="mb-1.5 block text-sm font-bold text-ink">행사 소개</label>
                  <Textarea id="infoDescription" value={fairInfo.description} onChange={(event) => updateFairInfoField("description", event.target.value)} placeholder="행사를 소개해 주세요." />
                </div>
                <div>
                  <label htmlFor="infoNoticeText" className="mb-1.5 block text-sm font-bold text-ink">관람 안내사항</label>
                  <Textarea id="infoNoticeText" value={fairInfo.noticeText} onChange={(event) => updateFairInfoField("noticeText", event.target.value)} placeholder="방문객이 꼭 알아야 할 관람 안내사항을 입력해 주세요." />
                </div>
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label htmlFor="infoPlaceName" className="mb-1.5 block text-sm font-bold text-ink">장소명</label>
                <Input id="infoPlaceName" value={fairInfo.placeName} onChange={(event) => updateFairInfoField("placeName", event.target.value)} placeholder="예: 서울 코엑스 C홀" />
              </div>
              <div>
                <label htmlFor="infoIndoorOutdoor" className="mb-1.5 block text-sm font-bold text-ink">실내/실외</label>
                <Select id="infoIndoorOutdoor" value={fairInfo.indoorOutdoor} onChange={(event) => updateFairInfoField("indoorOutdoor", event.target.value as FairInfoFormState["indoorOutdoor"])}>
                  <option value="">선택 안 함</option>
                  <option value="INDOOR">실내</option>
                  <option value="OUTDOOR">실외</option>
                </Select>
              </div>
            </div>
            <div>
              <label htmlFor="infoAddress" className="mb-1.5 block text-sm font-bold text-ink">주소</label>
              <Input id="infoAddress" value={fairInfo.address} onChange={(event) => updateFairInfoField("address", event.target.value)} placeholder="상세 주소를 입력해 주세요." />
            </div>

            <div>
              <p className="mb-1.5 text-sm font-bold text-ink">행사 운영 기간</p>
              <div className="flex max-w-md items-center gap-3">
                <Input id="infoOperationStartDate" type="date" aria-label="행사 운영 시작일" value={fairInfo.operationStartDate} onChange={(event) => updateFairInfoField("operationStartDate", event.target.value)} className="min-w-0 flex-1" />
                <span className="shrink-0 text-sm text-muted" aria-hidden="true">~</span>
                <Input id="infoOperationEndDate" type="date" aria-label="행사 운영 종료일" value={fairInfo.operationEndDate} onChange={(event) => updateFairInfoField("operationEndDate", event.target.value)} className="min-w-0 flex-1" />
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label htmlFor="infoManagerName" className="mb-1.5 block text-sm font-bold text-ink">담당자 이름<span className="ml-1 text-primary-strong">*</span></label>
                <Input id="infoManagerName" value={fairInfo.managerName} onChange={(event) => updateFairInfoField("managerName", event.target.value)} required />
              </div>
              <div>
                <label htmlFor="infoManagerPhone" className="mb-1.5 block text-sm font-bold text-ink">담당자 연락처</label>
                <Input id="infoManagerPhone" value={fairInfo.managerPhone} onChange={(event) => updateFairInfoField("managerPhone", event.target.value)} placeholder="예: 010-1234-5678" />
              </div>
            </div>

            <div className="flex justify-end">
              <Button type="submit" disabled={savingInfo}>{savingInfo ? "저장 중..." : "저장"}</Button>
            </div>
          </form>
        </div>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} title={editingFairDate ? "운영일 수정" : "운영일 추가"}>
        <form onSubmit={handleSaveFairDate} className="space-y-4">
          {formError && <p className="text-sm font-bold text-primary-strong">{formError}</p>}

          {editingFairDate && editingFairDate.reservedCount > 0 && (
            <div className="flex items-start gap-2 rounded-button border border-primary-strong/30 bg-primary-soft p-3 text-xs text-primary-strong">
              <AlertTriangle size={16} className="mt-0.5 shrink-0" />
              <p>이미 {editingFairDate.reservedCount}건 예약된 운영일이에요. 정원을 예약 건수보다 적게 줄일 수 없어요.</p>
            </div>
          )}

          <div>
            <label htmlFor="operationDate" className="mb-1.5 block text-sm font-bold text-ink">운영 날짜<span className="ml-1 text-primary-strong">*</span></label>
            {editingFairDate ? (
              <p className="text-sm text-muted">{form.operationDate} (날짜는 수정할 수 없어요. 바꾸려면 삭제 후 다시 등록해 주세요.)</p>
            ) : (
              <Input
                id="operationDate"
                type="date"
                value={form.operationDate}
                onChange={(event) => setForm((previous) => ({ ...previous, operationDate: event.target.value }))}
                required
              />
            )}
          </div>
          <div>
            <label htmlFor="capacity" className="mb-1.5 block text-sm font-bold text-ink">정원<span className="ml-1 text-primary-strong">*</span></label>
            <Input
              id="capacity"
              type="number"
              min={1}
              value={form.capacity}
              onChange={(event) => setForm((previous) => ({ ...previous, capacity: event.target.value }))}
              placeholder="예: 500"
              required
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="entryStartTime" className="mb-1.5 block text-sm font-bold text-ink">입장 시작<span className="ml-1 text-primary-strong">*</span></label>
              <Input
                id="entryStartTime"
                type="time"
                value={form.entryStartTime}
                onChange={(event) => setForm((previous) => ({ ...previous, entryStartTime: event.target.value }))}
                required
              />
            </div>
            <div>
              <label htmlFor="entryEndTime" className="mb-1.5 block text-sm font-bold text-ink">입장 종료<span className="ml-1 text-primary-strong">*</span></label>
              <Input
                id="entryEndTime"
                type="time"
                value={form.entryEndTime}
                onChange={(event) => setForm((previous) => ({ ...previous, entryEndTime: event.target.value }))}
                required
              />
            </div>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>취소</Button>
            <Button type="submit" disabled={saving}>{saving ? "저장 중..." : "저장"}</Button>
          </div>
        </form>
      </Dialog>
      {confirmDialog}
    </div>
  );
}
