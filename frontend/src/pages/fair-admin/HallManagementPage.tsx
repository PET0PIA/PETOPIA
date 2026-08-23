import { AlertCircle, LayoutGrid, Pencil, Plus, Trash2 } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../api/client";
import { createHall, deleteHall, getHalls, updateHall, type Hall, type HallInput } from "../../api/fair";
import { EmptyState } from "../../components/common/EmptyState";
import { PageHeader } from "../../components/common/PageHeader";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { ImageUploadField } from "../../components/ui/ImageUploadField";
import { Input } from "../../components/ui/Input";
import { Table } from "../../components/ui/Table";
import { useConfirm } from "../../components/ui/useConfirm";
import { useFairSelector } from "../../contexts/FairSelectorContext";

export function HallManagementPage() {
  const { confirm, confirmDialog } = useConfirm();
  const { fairId } = useFairSelector();

  const [halls, setHalls] = useState<Hall[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingHall, setEditingHall] = useState<Hall | null>(null);
  const [hallName, setHallName] = useState("");
  const [floorPlanImageObjectKey, setFloorPlanImageObjectKey] = useState<string | null>(null);
  const [floorPlanImageUploading, setFloorPlanImageUploading] = useState(false);
  const [existingFloorPlanImageUrl, setExistingFloorPlanImageUrl] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (fairId === null) return;
    let ignore = false;

    setLoading(true);
    setLoadError(null);
    setActionError(null);
    setHalls([]);
    getHalls(fairId)
      .then((data) => { if (!ignore) setHalls(data); })
      .catch((error) => { if (!ignore) setLoadError(error instanceof ApiError ? error.message : "홀 목록을 불러오지 못했어요."); })
      .finally(() => { if (!ignore) setLoading(false); });

    return () => { ignore = true; };
  }, [fairId]);

  function openCreateDialog() {
    setEditingHall(null);
    setHallName("");
    setFloorPlanImageObjectKey(null);
    setFloorPlanImageUploading(false);
    setExistingFloorPlanImageUrl(null);
    setFormError(null);
    setDialogOpen(true);
  }

  function openEditDialog(hall: Hall) {
    setEditingHall(hall);
    setHallName(hall.name);
    setFloorPlanImageObjectKey(null);
    setFloorPlanImageUploading(false);
    setExistingFloorPlanImageUrl(hall.floorPlanImageUrl);
    setFormError(null);
    setDialogOpen(true);
  }

  async function handleSaveHall(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (fairId === null) return;
    if (hallName.trim() === "") {
      setFormError("홀 이름을 입력해 주세요.");
      return;
    }
    if (floorPlanImageUploading) {
      setFormError("도면 이미지 업로드가 끝날 때까지 잠시만 기다려 주세요.");
      return;
    }

    const payload: HallInput = { name: hallName.trim(), floorPlanImageObjectKey: floorPlanImageObjectKey ?? undefined };

    setSaving(true);
    setFormError(null);
    try {
      const saved = editingHall
        ? await updateHall(fairId, editingHall.hallId, payload)
        : await createHall(fairId, payload);
      setHalls((previous) => editingHall
        ? previous.map((hall) => (hall.hallId === saved.hallId ? saved : hall))
        : [...previous, saved]);
      setDialogOpen(false);
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : "홀 정보를 저장하지 못했어요.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDeleteHall(hall: Hall) {
    if (fairId === null) return;
    if (!(await confirm({ description: `'${hall.name}' 홀을 삭제할까요?` }))) return;

    setActionError(null);
    try {
      await deleteHall(fairId, hall.hallId);
      setHalls((previous) => previous.filter((item) => item.hallId !== hall.hallId));
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : "홀을 삭제하지 못했어요.");
    }
  }

  return (
    <div className="mx-auto max-w-6xl py-2">
      <PageHeader
        eyebrow="행사 관리자"
        title="홀 관리"
        description="행사장의 홀을 등록·관리해요. 부스 배치는 홀 등록 이후 단계에서 진행돼요."
        action={fairId !== null ? <Button onClick={openCreateDialog}><Plus size={16} />홀 추가</Button> : undefined}
      />

      {loadError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{loadError}</p>
        </div>
      )}

      {actionError && (
        <div className="surface mb-6 flex items-start gap-3 border-primary-strong/30 bg-primary-soft p-4 text-sm text-primary-strong">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <p>{actionError}</p>
        </div>
      )}

      {fairId === null && (
        <EmptyState title="관리할 행사가 없어요." description="상단 바에서 행사를 선택하면 홀 목록이 표시돼요. 배정된 행사가 없다면 관리자에게 문의해 주세요." />
      )}

      {fairId !== null && loading && (
        <div className="surface grid min-h-40 place-items-center text-sm text-muted">홀 목록을 불러오는 중이에요...</div>
      )}

      {fairId !== null && !loading && !loadError && halls.length === 0 && (
        <EmptyState title="등록된 홀이 없어요." description="홀 추가 버튼을 눌러 첫 홀을 등록해 보세요." />
      )}

      {fairId !== null && !loading && !loadError && halls.length > 0 && (
        <Table>
          <thead>
            <tr className="border-b border-line text-xs font-bold text-muted">
              <th className="px-4 py-3">홀 이름</th>
              <th className="px-4 py-3">도면 이미지</th>
              <th className="px-4 py-3">등록일</th>
              <th className="px-4 py-3 text-right">관리</th>
            </tr>
          </thead>
          <tbody>
            {halls.map((hall) => (
              <tr key={hall.hallId} className="border-b border-line last:border-0">
                <td className="px-4 py-3 font-bold">{hall.name}</td>
                <td className="px-4 py-3 text-muted">{hall.floorPlanImageUrl ? "등록됨" : "미등록"}</td>
                <td className="px-4 py-3 text-muted">{new Date(hall.createdAt).toLocaleDateString("ko-KR")}</td>
                <td className="px-4 py-3">
                  <div className="flex justify-end gap-2">
                    <Link to={`/fair-admin/booths/${fairId}/${hall.hallId}`} aria-label={`${hall.name} 부스 배치 편집`} className="rounded-button p-2 text-muted hover:bg-page hover:text-ink">
                      <LayoutGrid size={16} />
                    </Link>
                    <button type="button" aria-label={`${hall.name} 수정`} className="rounded-button p-2 text-muted hover:bg-page hover:text-ink" onClick={() => openEditDialog(hall)}>
                      <Pencil size={16} />
                    </button>
                    <button type="button" aria-label={`${hall.name} 삭제`} className="rounded-button p-2 text-muted hover:bg-page hover:text-primary-strong" onClick={() => handleDeleteHall(hall)}>
                      <Trash2 size={16} />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} title={editingHall ? "홀 수정" : "홀 추가"}>
        <form onSubmit={handleSaveHall} className="space-y-4">
          {formError && <p className="text-sm font-bold text-primary-strong">{formError}</p>}
          <div>
            <label htmlFor="hallName" className="mb-1.5 block text-sm font-bold text-ink">홀 이름<span className="ml-1 text-primary-strong">*</span></label>
            <Input
              id="hallName"
              value={hallName}
              onChange={(event) => setHallName(event.target.value)}
              placeholder="예: A홀"
              required
            />
          </div>
          <ImageUploadField
            label="도면 이미지"
            initialImageUrl={existingFloorPlanImageUrl}
            onObjectKeyChange={setFloorPlanImageObjectKey}
            onUploadingChange={setFloorPlanImageUploading}
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>취소</Button>
            <Button type="submit" disabled={saving || floorPlanImageUploading}>
              {saving ? "저장 중..." : floorPlanImageUploading ? "이미지 업로드 중..." : "저장"}
            </Button>
          </div>
        </form>
      </Dialog>
      {confirmDialog}
    </div>
  );
}
