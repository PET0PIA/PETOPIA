import { AlertCircle, ArrowLeft, Copy, Grid3x3, Lock, Plus, RotateCcw, Save, Trash2, ZoomIn, ZoomOut } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ApiError } from "../../api/client";
import { bulkSaveBoothSlots, getBoothSlots, getHalls, type BoothSlot, type BoothSlotItem, type Hall } from "../../api/fair";
import { PageHeader } from "../../components/common/PageHeader";
import { BoothCanvas, MIN_SLOT_SIZE, clamp, type DraftSlot } from "../../components/fair-admin/BoothCanvas";
import { Button } from "../../components/ui/Button";
import { Input } from "../../components/ui/Input";
import { Textarea } from "../../components/ui/Textarea";

const ZOOM_MIN = 0.5;
const ZOOM_MAX = 2;
const ZOOM_STEP = 0.25;

let localKeySeed = 0;
function nextLocalKey() {
  localKeySeed += 1;
  return `new-${Date.now()}-${localKeySeed}`;
}

function toDraft(slot: BoothSlot): DraftSlot {
  return {
    key: `existing-${slot.boothSlotId}`,
    boothSlotId: slot.boothSlotId,
    slotNumber: slot.slotNumber,
    posX: slot.posX,
    posY: slot.posY,
    width: slot.width,
    height: slot.height,
    price: slot.price,
    memo: slot.memo ?? "",
    lockedAt: slot.lockedAt,
  };
}

function toItem(draft: DraftSlot): BoothSlotItem {
  const trimmedMemo = draft.memo.trim();
  return {
    boothSlotId: draft.boothSlotId ?? undefined,
    slotNumber: draft.slotNumber.trim(),
    posX: draft.posX,
    posY: draft.posY,
    width: draft.width,
    height: draft.height,
    price: draft.price,
    memo: trimmedMemo.length > 0 ? trimmedMemo : undefined,
  };
}

/** 복제 시 원본과 번호가 겹치지 않도록 "-2", "-3" ... 접미사를 붙여 비어있는 번호를 찾는다. */
function generateDuplicateSlotNumber(baseSlotNumber: string, existing: DraftSlot[]): string {
  const base = baseSlotNumber.trim();
  if (base.length === 0) return "";

  const taken = new Set(existing.map((draft) => draft.slotNumber.trim()));
  let suffix = 2;
  let candidate = `${base}-${suffix}`;
  while (taken.has(candidate)) {
    suffix += 1;
    candidate = `${base}-${suffix}`;
  }
  return candidate;
}

function validateDrafts(drafts: DraftSlot[]): string | null {
  const seenNumbers = new Set<string>();
  for (const draft of drafts) {
    const slotNumber = draft.slotNumber.trim();
    if (slotNumber.length === 0) return "모든 부스 슬롯에 부스 번호를 입력해 주세요.";
    if (seenNumbers.has(slotNumber)) return `부스 번호가 중복돼요: ${slotNumber}`;
    seenNumbers.add(slotNumber);
    if (!Number.isFinite(draft.price) || draft.price < 0) return `'${slotNumber}' 슬롯의 가격은 0 이상이어야 해요.`;
    for (const value of [draft.posX, draft.posY, draft.width, draft.height]) {
      if (value < 0 || value > 1) return `'${slotNumber}' 슬롯의 위치·크기가 배치 영역을 벗어났어요.`;
    }
  }
  return null;
}

export function BoothLayoutEditPage() {
  const params = useParams<{ fairId: string; hallId: string }>();
  const fairId = Number(params.fairId);
  const hallId = Number(params.hallId);
  const paramsValid = Number.isInteger(fairId) && fairId > 0 && Number.isInteger(hallId) && hallId > 0;

  const [hall, setHall] = useState<Hall | null>(null);
  const [drafts, setDrafts] = useState<DraftSlot[]>([]);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [reloadTick, setReloadTick] = useState(0);
  const [showGrid, setShowGrid] = useState(true);
  const [gridSize, setGridSize] = useState(5);
  const [zoom, setZoom] = useState(1);

  useEffect(() => {
    if (!paramsValid) return;
    let ignore = false;

    setLoading(true);
    setLoadError(null);
    Promise.all([getHalls(fairId), getBoothSlots(fairId, hallId)])
      .then(([halls, slots]) => {
        if (ignore) return;
        setHall(halls.find((item) => item.hallId === hallId) ?? null);
        setDrafts(slots.map(toDraft));
        setDirty(false);
        setSelectedKey(null);
      })
      .catch((error) => {
        if (ignore) return;
        setLoadError(error instanceof ApiError ? error.message : "부스 배치를 불러오지 못했어요.");
      })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [fairId, hallId, paramsValid, reloadTick]);

  useEffect(() => {
    function handleBeforeUnload(event: BeforeUnloadEvent) {
      if (!dirty) return;
      event.preventDefault();
      event.returnValue = "";
    }
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [dirty]);

  const selected = useMemo(() => drafts.find((draft) => draft.key === selectedKey) ?? null, [drafts, selectedKey]);

  function updateDraft(key: string, patch: Partial<DraftSlot>) {
    setDrafts((previous) => previous.map((draft) => (draft.key === key ? { ...draft, ...patch } : draft)));
    setDirty(true);
  }

  function handleAddSlot() {
    const key = nextLocalKey();
    const newDraft: DraftSlot = {
      key,
      boothSlotId: null,
      slotNumber: "",
      posX: clamp(0.4 + drafts.length * 0.02, 0, 1 - 0.15),
      posY: clamp(0.4 + drafts.length * 0.02, 0, 1 - 0.12),
      width: 0.15,
      height: 0.12,
      price: 0,
      memo: "",
      lockedAt: null,
    };
    setDrafts((previous) => [...previous, newDraft]);
    setSelectedKey(key);
    setDirty(true);
  }

  /**
   * 선택된 슬롯의 위치·크기를 %(0~100) 입력값으로 직접 수정한다. 드래그·리사이즈와
   * 동일한 clamp 규칙(0~1 범위, 슬롯이 캔버스 밖으로 나가지 않도록)을 그대로 적용해서
   * 마우스로 옮긴 것과 숫자로 입력한 것의 결과가 항상 일치하게 한다.
   */
  function handleGeometryFieldChange(field: "posX" | "posY" | "width" | "height", rawValue: string) {
    if (!selected) return;
    const percent = Number(rawValue);
    if (!Number.isFinite(percent)) return;
    const fraction = percent / 100;

    if (field === "posX") {
      updateDraft(selected.key, { posX: clamp(fraction, 0, Math.max(0, 1 - selected.width)) });
    } else if (field === "posY") {
      updateDraft(selected.key, { posY: clamp(fraction, 0, Math.max(0, 1 - selected.height)) });
    } else if (field === "width") {
      updateDraft(selected.key, { width: clamp(fraction, MIN_SLOT_SIZE, Math.max(MIN_SLOT_SIZE, 1 - selected.posX)) });
    } else {
      updateDraft(selected.key, { height: clamp(fraction, MIN_SLOT_SIZE, Math.max(MIN_SLOT_SIZE, 1 - selected.posY)) });
    }
  }

  /**
   * 선택된 슬롯과 동일한 크기·가격·메모를 가진 새 슬롯을 만든다. 같은 부스가 여러 개일 때
   * 하나씩 새로 추가하지 않고 복제해서 위치·번호만 손보게 하기 위함. 복제본은 항상 새
   * 슬롯(boothSlotId 없음, 잠금 없음)이라 원본이 잠겨 있어도 복제 자체는 막지 않는다.
   */
  function handleDuplicateSelected() {
    if (!selected) return;
    const key = nextLocalKey();
    const offset = 0.03;
    const duplicate: DraftSlot = {
      key,
      boothSlotId: null,
      slotNumber: generateDuplicateSlotNumber(selected.slotNumber, drafts),
      posX: clamp(selected.posX + offset, 0, Math.max(0, 1 - selected.width)),
      posY: clamp(selected.posY + offset, 0, Math.max(0, 1 - selected.height)),
      width: selected.width,
      height: selected.height,
      price: selected.price,
      memo: selected.memo,
      lockedAt: null,
    };
    setDrafts((previous) => [...previous, duplicate]);
    setSelectedKey(key);
    setDirty(true);
  }

  function handleDeleteSelected() {
    if (!selected) return;
    if (selected.lockedAt) {
      setFormError("이미 참가 신청이 걸린 슬롯은 삭제할 수 없어요.");
      return;
    }
    if (!window.confirm(`'${selected.slotNumber || "번호 없음"}' 슬롯을 삭제할까요?`)) return;
    setDrafts((previous) => previous.filter((draft) => draft.key !== selected.key));
    setSelectedKey(null);
    setDirty(true);
  }

  function handleZoomIn() {
    setZoom((value) => clamp(Math.round((value + ZOOM_STEP) * 100) / 100, ZOOM_MIN, ZOOM_MAX));
  }

  function handleZoomOut() {
    setZoom((value) => clamp(Math.round((value - ZOOM_STEP) * 100) / 100, ZOOM_MIN, ZOOM_MAX));
  }

  function handleZoomReset() {
    setZoom(1);
  }

  function handleReset() {
    if (dirty && !window.confirm("저장하지 않은 변경사항을 모두 되돌릴까요?")) return;
    setFormError(null);
    setReloadTick((tick) => tick + 1);
  }

  async function handleSave() {
    const validationError = validateDrafts(drafts);
    if (validationError) {
      setFormError(validationError);
      return;
    }

    setFormError(null);
    setSaving(true);
    try {
      const saved = await bulkSaveBoothSlots(fairId, hallId, { slots: drafts.map(toItem) });
      setDrafts(saved.map(toDraft));
      setSelectedKey(null);
      setDirty(false);
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : "부스 배치를 저장하지 못했어요.");
    } finally {
      setSaving(false);
    }
  }

  if (!paramsValid) {
    return (
      <div className="mx-auto max-w-6xl py-2">
        <div className="surface flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>잘못된 접근이에요. 행사 ID와 홀 ID가 필요해요.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl py-2">
      <Link to="/fair-admin/booths" className="mb-4 inline-flex items-center gap-1 text-sm font-bold text-muted hover:text-ink">
        <ArrowLeft size={16} />홀 목록으로
      </Link>

      <PageHeader
        eyebrow="박람회 관리자"
        title={hall ? `${hall.name} 부스 배치 편집` : "부스 배치 편집"}
        description="슬롯을 드래그해 위치를 옮기고, 우측 하단 손잡이로 크기를 조절해요. 자물쇠 아이콘이 붙은 슬롯은 이미 참가 신청이 걸려 있어 위치·크기·번호·가격을 바꿀 수 없어요."
        action={
          <div className="flex gap-2">
            <Button variant="outline" onClick={handleReset} disabled={loading || saving}>
              <RotateCcw size={16} />되돌리기
            </Button>
            <Button onClick={handleSave} disabled={loading || saving}>
              <Save size={16} />{saving ? "저장 중..." : "저장"}
            </Button>
          </div>
        }
      />

      {(loadError || formError) && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError ?? formError}</p>
        </div>
      )}

      {dirty && !loadError && (
        <p className="mb-4 text-sm font-bold text-primary-strong">저장하지 않은 변경사항이 있어요.</p>
      )}

      {loading ? (
        <div className="surface grid min-h-72 place-items-center text-sm text-muted">부스 배치를 불러오는 중이에요...</div>
      ) : (
        <div className="flex flex-col gap-6 lg:flex-row">
          <div className="min-w-0 flex-1">
            <div className="surface p-4">
              <div className="mb-3 flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => setShowGrid((value) => !value)}
                  aria-pressed={showGrid}
                  className={`inline-flex min-h-9 items-center gap-2 rounded-button border px-3 text-xs font-bold transition ${
                    showGrid ? "border-primary-strong/40 bg-primary-soft text-primary-strong" : "border-line bg-card text-muted hover:bg-page"
                  }`}
                >
                  <Grid3x3 size={14} />격자
                </button>
                {showGrid && (
                  <div className="flex items-center gap-1 rounded-button border border-line p-1">
                    {[10, 5, 2].map((size) => (
                      <button
                        key={size}
                        type="button"
                        onClick={() => setGridSize(size)}
                        className={`rounded-button px-2.5 py-1 text-xs font-bold transition ${
                          gridSize === size ? "bg-primary-soft text-primary-strong" : "text-muted hover:bg-page"
                        }`}
                      >
                        {size}%
                      </button>
                    ))}
                  </div>
                )}
                {showGrid && <span className="text-xs text-muted">드래그가 격자에 맞춰져요. Alt를 누른 채 드래그하면 스냅 없이 움직여요.</span>}

                <div className="ml-auto flex items-center gap-1 rounded-button border border-line p-1">
                  <button
                    type="button"
                    onClick={handleZoomOut}
                    disabled={zoom <= ZOOM_MIN}
                    aria-label="축소"
                    className="rounded-button p-1.5 text-muted hover:bg-page hover:text-ink disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    <ZoomOut size={14} />
                  </button>
                  <button
                    type="button"
                    onClick={handleZoomReset}
                    title="100%로 초기화"
                    className="min-w-12 rounded-button px-1.5 py-1 text-xs font-bold text-muted hover:bg-page hover:text-ink"
                  >
                    {Math.round(zoom * 100)}%
                  </button>
                  <button
                    type="button"
                    onClick={handleZoomIn}
                    disabled={zoom >= ZOOM_MAX}
                    aria-label="확대"
                    className="rounded-button p-1.5 text-muted hover:bg-page hover:text-ink disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    <ZoomIn size={14} />
                  </button>
                </div>
              </div>
              <BoothCanvas
                slots={drafts}
                selectedKey={selectedKey}
                backgroundImageUrl={hall?.floorPlanImageUrl}
                showGrid={showGrid}
                gridSize={gridSize}
                zoom={zoom}
                onSelect={setSelectedKey}
                onGeometryChange={updateDraft}
              />
              {zoom !== 1 && <p className="mt-2 text-xs text-muted">캔버스를 스크롤해서 확대된 영역을 볼 수 있어요.</p>}
            </div>
          </div>

          <aside className="w-full shrink-0 space-y-4 lg:w-80">
            <div className="surface p-4">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="text-sm font-extrabold">부스 슬롯 ({drafts.length})</h2>
                <Button variant="outline" onClick={handleAddSlot}><Plus size={14} />추가</Button>
              </div>
              {drafts.length === 0 ? (
                <p className="text-sm text-muted">아직 등록된 슬롯이 없어요.</p>
              ) : (
                <ul className="max-h-52 space-y-1 overflow-y-auto">
                  {drafts.map((draft) => (
                    <li key={draft.key}>
                      <button
                        type="button"
                        onClick={() => setSelectedKey(draft.key)}
                        className={`flex w-full items-center gap-2 rounded-button px-3 py-2 text-left text-sm font-bold ${
                          draft.key === selectedKey ? "bg-primary-soft text-primary-strong" : "text-ink hover:bg-page"
                        }`}
                      >
                        {draft.lockedAt && <Lock size={12} className="shrink-0 text-muted" />}
                        <span className="truncate">{draft.slotNumber || "(번호 미입력)"}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {selected && (
              <div className="surface space-y-4 p-4">
                <div className="flex items-center justify-between">
                  <h2 className="text-sm font-extrabold">슬롯 정보</h2>
                  {selected.lockedAt && (
                    <span className="inline-flex items-center gap-1 rounded-full bg-page px-2.5 py-1 text-xs font-bold text-muted">
                      <Lock size={12} />신청 확정
                    </span>
                  )}
                </div>

                <div>
                  <span className="mb-1.5 block text-sm font-bold text-ink">부스 번호<span className="ml-1 text-primary-strong">*</span></span>
                  <Input
                    value={selected.slotNumber}
                    onChange={(event) => updateDraft(selected.key, { slotNumber: event.target.value })}
                    placeholder="예: A-01"
                    disabled={!!selected.lockedAt}
                  />
                </div>

                <div>
                  <span className="mb-1.5 block text-sm font-bold text-ink">가격(원)<span className="ml-1 text-primary-strong">*</span></span>
                  <Input
                    type="number"
                    min={0}
                    step={1000}
                    value={selected.price}
                    onChange={(event) => updateDraft(selected.key, { price: Number(event.target.value) })}
                    disabled={!!selected.lockedAt}
                  />
                </div>

                <div>
                  <span className="mb-1.5 block text-sm font-bold text-ink">메모</span>
                  <Textarea
                    value={selected.memo}
                    onChange={(event) => updateDraft(selected.key, { memo: event.target.value })}
                    placeholder="전기 사용 가능, 코너 자리 등"
                  />
                </div>

                <div>
                  <span className="mb-1.5 block text-sm font-bold text-ink">위치·크기(%)</span>
                  <div className="grid grid-cols-2 gap-2">
                    <label className="text-xs text-muted">
                      X
                      <Input
                        type="number"
                        min={0}
                        max={100}
                        step={0.5}
                        value={Math.round(selected.posX * 1000) / 10}
                        onChange={(event) => handleGeometryFieldChange("posX", event.target.value)}
                        disabled={!!selected.lockedAt}
                        className="mt-1"
                      />
                    </label>
                    <label className="text-xs text-muted">
                      Y
                      <Input
                        type="number"
                        min={0}
                        max={100}
                        step={0.5}
                        value={Math.round(selected.posY * 1000) / 10}
                        onChange={(event) => handleGeometryFieldChange("posY", event.target.value)}
                        disabled={!!selected.lockedAt}
                        className="mt-1"
                      />
                    </label>
                    <label className="text-xs text-muted">
                      너비
                      <Input
                        type="number"
                        min={MIN_SLOT_SIZE * 100}
                        max={100}
                        step={0.5}
                        value={Math.round(selected.width * 1000) / 10}
                        onChange={(event) => handleGeometryFieldChange("width", event.target.value)}
                        disabled={!!selected.lockedAt}
                        className="mt-1"
                      />
                    </label>
                    <label className="text-xs text-muted">
                      높이
                      <Input
                        type="number"
                        min={MIN_SLOT_SIZE * 100}
                        max={100}
                        step={0.5}
                        value={Math.round(selected.height * 1000) / 10}
                        onChange={(event) => handleGeometryFieldChange("height", event.target.value)}
                        disabled={!!selected.lockedAt}
                        className="mt-1"
                      />
                    </label>
                  </div>
                  {!selected.lockedAt && (
                    <p className="mt-1.5 text-xs text-muted">캔버스에서 드래그해도 되고, 여기에 직접 %를 입력해도 돼요. 최소 크기는 {(MIN_SLOT_SIZE * 100).toFixed(0)}%예요.</p>
                  )}
                </div>

                <div className="flex gap-2">
                  <Button variant="outline" className="flex-1" onClick={handleDuplicateSelected}>
                    <Copy size={16} />복제
                  </Button>
                  <Button variant="outline" className="flex-1" onClick={handleDeleteSelected} disabled={!!selected.lockedAt}>
                    <Trash2 size={16} />삭제
                  </Button>
                </div>
              </div>
            )}
          </aside>
        </div>
      )}
    </div>
  );
}
