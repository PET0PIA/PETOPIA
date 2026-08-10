import { AlertCircle, AlertTriangle, Pencil, Plus, Search, Trash2 } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import {
  createFairDate,
  deleteFairDate,
  getFairDates,
  updateFairDate,
  type CreateFairDateRequest,
  type FairDate,
  type UpdateFairDateRequest,
} from "../../api/fair";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { Input } from "../../components/ui/Input";
import { Table } from "../../components/ui/Table";
import { useConfirm } from "../../components/ui/useConfirm";

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

  // TODO 관리자 세션에 현재 담당 행사(fairId)가 연결되면 이 입력을 없애고 세션 값을 바로 쓴다.
  const [fairIdInput, setFairIdInput] = useState("");
  const [fairId, setFairId] = useState<number | null>(null);

  const [fairDates, setFairDates] = useState<FairDate[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  // fairId가 이전과 같은 값이면 useState 갱신이 리렌더를 안 일으켜서 아래 조회 effect가
  // 다시 안 돈다. "불러오기"를 다시 눌렀을 때(같은 행사 ID라도) 최신 상태를 다시 받아오도록
  // 이 값을 강제로 바꿔 effect를 재실행시킨다.
  const [reloadTick, setReloadTick] = useState(0);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingFairDate, setEditingFairDate] = useState<FairDate | null>(null);
  const [form, setForm] = useState<FairDateFormState>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (fairId === null) return;
    let ignore = false;

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

    return () => { ignore = true; };
  }, [fairId, reloadTick]);

  function handleLoadFair(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = Number(fairIdInput);
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setLoadError("행사 ID는 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    setLoading(true);
    setLoadError(null);
    if (parsed === fairId) {
      setReloadTick((tick) => tick + 1);
    } else {
      setFairId(parsed);
    }
  }

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
      const proceed = await confirm({
        title: "정원을 예약 건수보다 적게 줄일까요?",
        description: `이미 ${editingFairDate.reservedCount}건 예약된 운영일이에요. 정원을 그보다 적은 ${capacity}명으로 줄이면 초과예약 상태가 될 수 있어요. 그래도 저장할까요?`,
        confirmLabel: "그래도 저장",
      });
      if (!proceed) return;
    }

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

    const warnings: string[] = [];
    if (fairDate.reservedCount > 0) warnings.push(`이미 예약이 ${fairDate.reservedCount}건 있어요`);
    if (fairDate.onsiteSalesConfigured) warnings.push("현장예매 정책이 설정돼 있어요");

    const description = warnings.length > 0
      ? `${fairDate.operationDate} 운영일은 ${warnings.join(", ")}. 삭제하면 관련 데이터와 어긋날 수 있어요. 그래도 삭제할까요?`
      : `${fairDate.operationDate} 운영일을 삭제할까요?`;
    const proceed = await confirm({
      title: "운영일을 삭제할까요?",
      description,
      confirmLabel: "삭제",
      danger: warnings.length > 0,
    });
    if (!proceed) return;

    try {
      await deleteFairDate(fairId, fairDate.fairDateId);
      setFairDates((previous) => previous.filter((item) => item.fairDateId !== fairDate.fairDateId));
    } catch (error) {
      setLoadError(error instanceof ApiError ? error.message : "운영일을 삭제하지 못했어요.");
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

      <form onSubmit={handleLoadFair} className="surface mb-6 flex flex-col gap-3 p-5 sm:flex-row sm:items-end">
        <div className="flex-1">
          <label htmlFor="fairIdInput" className="mb-1.5 block text-sm font-bold text-ink">관리할 행사 ID</label>
          <Input
            id="fairIdInput"
            type="number"
            min={1}
            value={fairIdInput}
            onChange={(event) => setFairIdInput(event.target.value)}
            placeholder="예: 1"
          />
        </div>
        <Button type="submit" variant="outline"><Search size={16} />불러오기</Button>
      </form>

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {fairId === null && (
        <EmptyState title="행사 ID를 먼저 입력해 주세요." description="관리할 행사의 ID를 입력하고 불러오기를 누르면 운영일 목록이 표시돼요." />
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
                    <button type="button" aria-label={`${fairDate.operationDate} 삭제`} className="rounded-button p-2 text-muted hover:bg-page hover:text-primary-strong" onClick={() => handleDeleteFairDate(fairDate)}>
                      <Trash2 size={16} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} title={editingFairDate ? "운영일 수정" : "운영일 추가"}>
        <form onSubmit={handleSaveFairDate} className="space-y-4">
          {formError && <p className="text-sm font-bold text-primary-strong">{formError}</p>}

          {editingFairDate && editingFairDate.reservedCount > 0 && (
            <div className="flex items-start gap-2 rounded-button border border-primary-strong/30 bg-primary-soft p-3 text-xs text-primary-strong">
              <AlertTriangle size={16} className="mt-0.5 shrink-0" />
              <p>이미 {editingFairDate.reservedCount}건 예약된 운영일이에요. 정원을 예약 건수보다 적게 줄이면 초과예약 상태가 될 수 있어요.</p>
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
