import { AlertCircle, ChevronLeft, ImageIcon, Pencil, Plus, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { Input } from "../../components/ui/Input";
import { Select } from "../../components/ui/Select";
import { Textarea } from "../../components/ui/Textarea";
import { ImageUploadField } from "../../components/ui/ImageUploadField";
import { useConfirm } from "../../components/ui/useConfirm";
import { ApiError } from "../../api/client";
import {
  getBooth,
  updateBooth,
  createBoothItem,
  updateBoothItem,
  deleteBoothItem,
  type BoothResponse,
  type BoothItemResponse,
  type BoothItemType,
  type BoothTargetAnimal,
} from "../../api/booth";

const targetAnimalLabels: Record<BoothTargetAnimal, string> = {
  DOG: "강아지",
  CAT: "고양이",
  ETC: "기타",
};

const itemTypeLabels: Record<BoothItemType, string> = {
  PRODUCT: "판매상품",
  EVENT: "이벤트",
  SAMPLE: "체험·샘플",
};

function BackLink() {
  const navigate = useNavigate();
  return (
    <button
      type="button"
      onClick={() => navigate(-1)}
      className="inline-flex min-h-11 items-center gap-1 text-sm font-bold text-muted hover:text-ink"
    >
      <ChevronLeft size={16} />
      부스 상세로
    </button>
  );
}

export function BoothEditPage() {
  const { boothId } = useParams<{ boothId: string }>();
  const id = Number(boothId);
  const idValid = Number.isInteger(id) && id > 0;

  if (!idValid) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <div className="mt-4">
          <EmptyState title="잘못된 부스 주소예요." description="부스 주소가 올바르지 않아요." actionTo="/" actionLabel="홈으로" />
        </div>
      </div>
    );
  }

  return <BoothEditContent key={id} id={id} />;
}

interface ItemFormState {
  boothItemId: number | null; // null이면 새 항목 추가
  name: string;
  type: BoothItemType;
  note: string;
  imageObjectKey: string | undefined;
  initialImageUrl: string | null;
}

const emptyItemForm: ItemFormState = {
  boothItemId: null,
  name: "",
  type: "PRODUCT",
  note: "",
  imageObjectKey: undefined,
  initialImageUrl: null,
};

function BoothEditContent({ id }: { id: number }) {
  const { confirm, confirmDialog } = useConfirm();

  const [booth, setBooth] = useState<BoothResponse | null>(null);
  const [items, setItems] = useState<BoothItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [accessDenied, setAccessDenied] = useState(false);

  // 프로필 폼 상태
  const [name, setName] = useState("");
  const [intro, setIntro] = useState("");
  const [category, setCategory] = useState("");
  const [targetAnimal, setTargetAnimal] = useState<BoothTargetAnimal | "">("");
  const [imageObjectKey, setImageObjectKey] = useState<string | undefined>(undefined);
  const [imageUploading, setImageUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // 상품·이벤트 다이얼로그 상태
  const [itemDialogOpen, setItemDialogOpen] = useState(false);
  const [itemForm, setItemForm] = useState<ItemFormState>(emptyItemForm);
  const [itemImageUploading, setItemImageUploading] = useState(false);
  const [itemSaving, setItemSaving] = useState(false);
  const [itemError, setItemError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    getBooth(id)
      .then((res) => {
        if (!alive) return;
        setBooth(res);
        setItems(res.items);
        setName(res.name);
        setIntro(res.intro ?? "");
        setCategory(res.category ?? "");
        setTargetAnimal(res.targetAnimal ?? "");
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setLoadError(err instanceof ApiError ? err.message : "부스를 불러오지 못했어요.");
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [id]);

  function handleAccessDenied(err: unknown): boolean {
    if (err instanceof ApiError && err.code === "V026") {
      setAccessDenied(true);
      return true;
    }
    return false;
  }

  const isDirty =
    booth !== null &&
    (name !== booth.name ||
      intro !== (booth.intro ?? "") ||
      category !== (booth.category ?? "") ||
      targetAnimal !== (booth.targetAnimal ?? "") ||
      imageObjectKey !== undefined);

  async function handleSaveProfile() {
    if (!booth || !isDirty) return;

    setSaving(true);
    setSaveError(null);
    try {
      const payload: Parameters<typeof updateBooth>[1] = {};
      if (name !== booth.name) payload.name = name;
      if (intro !== (booth.intro ?? "")) payload.intro = intro;
      if (category !== (booth.category ?? "")) payload.category = category;
      if (targetAnimal !== (booth.targetAnimal ?? "") && targetAnimal !== "") payload.targetAnimal = targetAnimal;
      if (imageObjectKey !== undefined) payload.imageObjectKey = imageObjectKey;

      const updated = await updateBooth(id, payload);
      setBooth(updated);
      setName(updated.name);
      setIntro(updated.intro ?? "");
      setCategory(updated.category ?? "");
      setTargetAnimal(updated.targetAnimal ?? "");
      setImageObjectKey(undefined);
    } catch (err) {
      if (handleAccessDenied(err)) return;
      setSaveError(err instanceof ApiError ? err.message : "저장에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSaving(false);
    }
  }

  function openAddItemDialog() {
    setItemForm(emptyItemForm);
    setItemError(null);
    setItemDialogOpen(true);
  }

  function openEditItemDialog(item: BoothItemResponse) {
    setItemForm({
      boothItemId: item.boothItemId,
      name: item.name,
      type: item.type,
      note: item.note ?? "",
      imageObjectKey: undefined,
      initialImageUrl: item.imageUrl,
    });
    setItemError(null);
    setItemDialogOpen(true);
  }

  async function handleItemSubmit() {
    if (itemForm.name.trim() === "") {
      setItemError("이름을 입력해 주세요.");
      return;
    }

    setItemSaving(true);
    setItemError(null);
    try {
      if (itemForm.boothItemId === null) {
        const created = await createBoothItem(id, {
          name: itemForm.name.trim(),
          type: itemForm.type,
          note: itemForm.note.trim() || undefined,
          imageObjectKey: itemForm.imageObjectKey,
        });
        setItems((prev) => [...prev, created]);
      } else {
        const updated = await updateBoothItem(itemForm.boothItemId, {
          name: itemForm.name.trim(),
          type: itemForm.type,
          note: itemForm.note.trim(),
          imageObjectKey: itemForm.imageObjectKey,
        });
        setItems((prev) => prev.map((it) => (it.boothItemId === updated.boothItemId ? updated : it)));
      }
      setItemDialogOpen(false);
    } catch (err) {
      if (handleAccessDenied(err)) {
        setItemDialogOpen(false);
        return;
      }
      setItemError(err instanceof ApiError ? err.message : "저장에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setItemSaving(false);
    }
  }

  async function handleDeleteItem(item: BoothItemResponse) {
    const proceed = await confirm({
      title: "상품·이벤트 삭제",
      description: `"${item.name}"을(를) 삭제할까요? 되돌릴 수 없어요.`,
      confirmLabel: "삭제",
    });
    if (!proceed) return;

    try {
      await deleteBoothItem(item.boothItemId);
      setItems((prev) => prev.filter((it) => it.boothItemId !== item.boothItemId));
    } catch (err) {
      if (handleAccessDenied(err)) return;
      setSaveError(err instanceof ApiError ? err.message : "삭제에 실패했어요. 잠시 후 다시 시도해 주세요.");
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <p className="py-16 text-center text-sm text-muted">부스를 불러오는 중이에요…</p>
      </div>
    );
  }

  if (loadError || !booth) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <div className="mt-4">
          <EmptyState title="부스를 찾을 수 없어요." description={loadError ?? "부스 정보를 불러올 수 없어요."} actionTo="/" actionLabel="홈으로" />
        </div>
      </div>
    );
  }

  if (accessDenied) {
    return (
      <div className="mx-auto max-w-3xl py-2">
        <BackLink />
        <div className="mt-4">
          <EmptyState
            title="이 부스를 수정할 권한이 없어요."
            description="본인 소유의 부스만 수정할 수 있어요."
            actionTo={`/booths/${id}`}
            actionLabel="부스 상세로"
          />
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl py-2">
      <BackLink />

      <h1 className="mt-4 mb-6 text-2xl font-extrabold tracking-tight text-ink sm:text-3xl">부스 관리</h1>

      <div className="space-y-6">
        <Card className="space-y-4 p-6">
          <h3 className="text-sm font-extrabold text-muted">프로필</h3>

          <ImageUploadField
            label="부스 이미지"
            initialImageUrl={booth.imageUrl}
            onObjectKeyChange={(key) => setImageObjectKey(key ?? undefined)}
            onUploadingChange={setImageUploading}
          />

          <div>
            <label className="mb-1.5 block text-sm font-bold text-ink">부스 이름</label>
            <Input value={name} onChange={(e) => setName(e.target.value)} />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-bold text-ink">소개</label>
            <Textarea value={intro} onChange={(e) => setIntro(e.target.value)} />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1.5 block text-sm font-bold text-ink">카테고리</label>
              <Input value={category} onChange={(e) => setCategory(e.target.value)} placeholder="사료/간식, 미용, 훈련, 굿즈 등" />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-bold text-ink">대상 동물</label>
              <Select value={targetAnimal} onChange={(e) => setTargetAnimal(e.target.value as BoothTargetAnimal | "")}>
                <option value="">선택 안 함</option>
                {(Object.keys(targetAnimalLabels) as BoothTargetAnimal[]).map((value) => (
                  <option key={value} value={value}>
                    {targetAnimalLabels[value]}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          {saveError && (
            <div className="flex items-start gap-2 text-sm text-primary-strong">
              <AlertCircle size={16} className="mt-0.5 shrink-0" />
              <p>{saveError}</p>
            </div>
          )}

          <div className="flex justify-end">
            <Button type="button" onClick={handleSaveProfile} disabled={!isDirty || saving || imageUploading}>
              {saving ? "저장 중..." : "저장"}
            </Button>
          </div>
        </Card>

        <Card className="space-y-4 p-6">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-extrabold text-muted">판매상품·이벤트 관리</h3>
            <Button type="button" variant="outline" onClick={openAddItemDialog}>
              <Plus size={16} />
              항목 추가
            </Button>
          </div>

          {items.length === 0 ? (
            <p className="text-sm text-muted">등록된 상품·이벤트가 없어요.</p>
          ) : (
            <div className="space-y-2">
              {items.map((item) => (
                <div key={item.boothItemId} className="flex items-center gap-3 rounded-button border border-line p-3">
                  <div className="grid size-12 shrink-0 place-items-center overflow-hidden rounded-button bg-page text-muted">
                    {item.imageUrl ? (
                      <img src={item.imageUrl} alt="" className="size-full object-cover" />
                    ) : (
                      <ImageIcon size={18} />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="mb-1 flex items-center gap-2">
                      <p className="truncate font-bold text-ink">{item.name}</p>
                      <Badge tone="neutral">{itemTypeLabels[item.type]}</Badge>
                    </div>
                    {item.note && <p className="truncate text-xs text-muted">{item.note}</p>}
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    <button type="button" onClick={() => openEditItemDialog(item)} className="grid size-9 place-items-center rounded-button text-muted hover:bg-page hover:text-ink" aria-label="수정">
                      <Pencil size={16} />
                    </button>
                    <button type="button" onClick={() => handleDeleteItem(item)} className="grid size-9 place-items-center rounded-button text-muted hover:bg-page hover:text-primary-strong" aria-label="삭제">
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      <Dialog open={itemDialogOpen} onClose={() => setItemDialogOpen(false)} title={itemForm.boothItemId === null ? "항목 추가" : "항목 수정"}>
        <div className="space-y-4">
          <ImageUploadField
            label="이미지"
            initialImageUrl={itemForm.initialImageUrl}
            onObjectKeyChange={(key) => setItemForm((prev) => ({ ...prev, imageObjectKey: key ?? undefined }))}
            onUploadingChange={setItemImageUploading}
          />
          <div>
            <label className="mb-1.5 block text-sm font-bold text-ink">이름<span className="ml-1 text-primary-strong">*</span></label>
            <Input value={itemForm.name} onChange={(e) => setItemForm((prev) => ({ ...prev, name: e.target.value }))} />
          </div>
          <div>
            <label className="mb-1.5 block text-sm font-bold text-ink">종류</label>
            <Select value={itemForm.type} onChange={(e) => setItemForm((prev) => ({ ...prev, type: e.target.value as BoothItemType }))}>
              {(Object.keys(itemTypeLabels) as BoothItemType[]).map((value) => (
                <option key={value} value={value}>
                  {itemTypeLabels[value]}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className="mb-1.5 block text-sm font-bold text-ink">비고</label>
            <Input value={itemForm.note} onChange={(e) => setItemForm((prev) => ({ ...prev, note: e.target.value }))} placeholder="예: 선착순 100개" />
          </div>
          {itemError && <p className="text-sm font-bold text-primary-strong">{itemError}</p>}
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setItemDialogOpen(false)}>닫기</Button>
            <Button type="button" onClick={handleItemSubmit} disabled={itemSaving || itemImageUploading}>
              {itemSaving ? "저장 중..." : "저장"}
            </Button>
          </div>
        </div>
      </Dialog>
      {confirmDialog}
    </div>
  );
}