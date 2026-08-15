import { AlertCircle, Pencil, Plus, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { ImageUploadField } from "../../components/ui/ImageUploadField";
import { Input } from "../../components/ui/Input";
import { Select } from "../../components/ui/Select";
import { Textarea } from "../../components/ui/Textarea";
import { useConfirm } from "../../components/ui/useConfirm";
import { ApiError } from "../../api/client";
import type { LinkTarget } from "../../api/banner";
import {
  createPopup,
  deletePopup,
  getAdminPopups,
  togglePopupActive,
  updatePopup,
  type Popup,
  type PopupCreateInput,
} from "../../api/popup";

interface FormState {
  title: string;
  subtitle: string;
  linkUrl: string;
  linkTarget: LinkTarget;
  linkLabel: string;
  bgColor: string;
  width: string;
  height: string;
  startedAt: string;
  endedAt: string;
}

const emptyForm: FormState = {
  title: "",
  subtitle: "",
  linkUrl: "",
  linkTarget: "SELF",
  linkLabel: "",
  bgColor: "",
  width: "",
  height: "",
  startedAt: "",
  endedAt: "",
};

function label(text: string, required = false) {
  return (
    <span className="mb-1.5 block text-sm font-bold text-ink">
      {text}
      {required && <span className="ml-1 text-primary-strong">*</span>}
    </span>
  );
}

function toDatetimeLocal(value: string | null): string {
  if (!value) return "";
  return value.length >= 16 ? value.slice(0, 16) : value;
}

function toIsoDateTime(value: string): string | undefined {
  if (value === "") return undefined;
  return value.length === 16 ? `${value}:00` : value;
}

function formatDateTime(value: string | null): string {
  return value ? value.slice(0, 16).replace("T", " ") : "제한 없음";
}

export function AdminPopupsPage() {
  const { confirm, confirmDialog } = useConfirm();

  const [popups, setPopups] = useState<Popup[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingPopup, setEditingPopup] = useState<Popup | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [existingImageUrl, setExistingImageUrl] = useState<string | null>(null);
  const [imageObjectKey, setImageObjectKey] = useState<string | null>(null);
  const [imageRemoved, setImageRemoved] = useState(false);
  const [imageUploading, setImageUploading] = useState(false);
  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);

  const [togglingId, setTogglingId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  function fetchPopups() {
    getAdminPopups()
      .then((data) => {
        setLoadError(null);
        setPopups(data);
      })
      .catch((error) => {
        setPopups(null);
        setLoadError(error instanceof ApiError ? error.message : "팝업 목록을 불러오지 못했어요.");
      })
      .finally(() => setLoading(false));
  }

  useEffect(fetchPopups, []);

  const activePopups = useMemo(() => (popups ?? []).filter((p) => p.active), [popups]);
  const inactivePopups = useMemo(() => (popups ?? []).filter((p) => !p.active), [popups]);

  function openCreateDialog() {
    setEditingPopup(null);
    setForm(emptyForm);
    setExistingImageUrl(null);
    setImageObjectKey(null);
    setImageRemoved(false);
    setImageUploading(false);
    setFormErrors([]);
    setDialogOpen(true);
  }

  function openEditDialog(popup: Popup) {
    setEditingPopup(popup);
    setForm({
      title: popup.title,
      subtitle: popup.subtitle ?? "",
      linkUrl: popup.linkUrl ?? "",
      linkTarget: popup.linkTarget,
      linkLabel: popup.linkLabel ?? "",
      bgColor: popup.bgColor ?? "",
      width: popup.width ? String(popup.width) : "",
      height: popup.height ? String(popup.height) : "",
      startedAt: toDatetimeLocal(popup.startedAt),
      endedAt: toDatetimeLocal(popup.endedAt),
    });
    setExistingImageUrl(popup.imageKey);
    setImageObjectKey(null);
    setImageRemoved(false);
    setImageUploading(false);
    setFormErrors([]);
    setDialogOpen(true);
  }

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }

  // 새 이미지를 고르면 "삭제했던" 상태는 취소된다.
  function handleImageObjectKeyChange(objectKey: string | null) {
    setImageObjectKey(objectKey);
    if (objectKey) setImageRemoved(false);
  }

  function handleImageRemove() {
    setImageRemoved(true);
  }

  function validate(): string[] {
    const errors: string[] = [];
    if (form.title.trim() === "") errors.push("제목을 입력해 주세요.");

    const hasImage = Boolean(imageObjectKey) || (Boolean(existingImageUrl) && !imageRemoved);
    const hasSubtitle = form.subtitle.trim() !== "";
    if (!hasImage && !hasSubtitle) errors.push("이미지 또는 부제 중 하나는 있어야 해요.");

    if (form.startedAt !== "" && form.endedAt !== "" && form.startedAt >= form.endedAt) {
      errors.push("노출 종료 일시는 시작 일시보다 뒤여야 해요.");
    }
    if (form.linkLabel.trim() !== "" && form.linkUrl.trim() === "") errors.push("버튼 문구를 넣으려면 이동 링크도 함께 입력해 주세요.");
    if (form.width !== "" && Number(form.width) <= 0) errors.push("너비는 1 이상이어야 해요.");
    if (form.height !== "" && Number(form.height) <= 0) errors.push("높이는 1 이상이어야 해요.");
    return errors;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validate();
    if (imageUploading) errors.push("이미지 업로드가 끝날 때까지 잠시만 기다려 주세요.");
    setFormErrors(errors);
    if (errors.length > 0) return;

    setSaving(true);
    try {
      if (editingPopup) {
        await updatePopup(editingPopup.popupId, {
          title: form.title.trim(),
          subtitle: form.subtitle.trim(),
          imageKey: imageObjectKey ?? (imageRemoved ? "" : undefined),
          linkUrl: form.linkUrl.trim(),
          linkTarget: form.linkTarget,
          linkLabel: form.linkLabel.trim(),
          bgColor: form.bgColor.trim(),
          width: form.width === "" ? undefined : Number(form.width),
          height: form.height === "" ? undefined : Number(form.height),
          startedAt: toIsoDateTime(form.startedAt),
          endedAt: toIsoDateTime(form.endedAt),
        });
      } else {
        const trimmedSubtitle = form.subtitle.trim();
        const common = {
          title: form.title.trim(),
          linkUrl: form.linkUrl.trim() || undefined,
          linkTarget: form.linkTarget,
          linkLabel: form.linkLabel.trim() || undefined,
          bgColor: form.bgColor.trim() || undefined,
          width: form.width === "" ? undefined : Number(form.width),
          height: form.height === "" ? undefined : Number(form.height),
          startedAt: toIsoDateTime(form.startedAt),
          endedAt: toIsoDateTime(form.endedAt),
        };
        // validate()가 이미 이미지·부제 중 하나는 있음을 보장하므로, 이미지가 있으면 부제는
        // 선택, 없으면 부제가 필수인 PopupCreateInput의 유니언 형태에 맞춰 나눠 구성한다.
        const payload: PopupCreateInput = imageObjectKey
          ? { ...common, imageKey: imageObjectKey, subtitle: trimmedSubtitle || undefined }
          : { ...common, subtitle: trimmedSubtitle };
        await createPopup(payload);
      }
      setDialogOpen(false);
      fetchPopups();
    } catch (error) {
      setFormErrors([error instanceof ApiError ? error.message : "팝업을 저장하지 못했어요."]);
    } finally {
      setSaving(false);
    }
  }

  async function handleToggle(popup: Popup) {
    setActionError(null);
    setTogglingId(popup.popupId);
    try {
      await togglePopupActive(popup.popupId, !popup.active);
      fetchPopups();
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : "노출 상태를 변경하지 못했어요.");
    } finally {
      setTogglingId(null);
    }
  }

  async function handleDelete(popup: Popup) {
    if (!(await confirm({ description: `'${popup.title}' 팝업을 삭제할까요?` }))) return;
    setActionError(null);
    setDeletingId(popup.popupId);
    try {
      await deletePopup(popup.popupId);
      fetchPopups();
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : "팝업을 삭제하지 못했어요.");
    } finally {
      setDeletingId(null);
    }
  }

  function renderRow(popup: Popup, faded: boolean) {
    return (
      <div key={popup.popupId} className={`surface flex items-center gap-3 p-3 ${faded ? "opacity-70" : ""}`}>
        {popup.imageKey ? (
          <img src={popup.imageKey} alt="" className="h-12 w-20 shrink-0 rounded-button object-cover" />
        ) : (
          <div className="grid h-12 w-20 shrink-0 place-items-center rounded-button bg-page text-xs text-muted">텍스트</div>
        )}
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-bold text-ink">{popup.title}</p>
          <p className="truncate text-xs text-muted">
            {formatDateTime(popup.startedAt)} ~ {formatDateTime(popup.endedAt)}
          </p>
          {faded && <Badge tone="neutral">비노출</Badge>}
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <Button variant="outline" disabled={togglingId === popup.popupId} onClick={() => handleToggle(popup)}>
            {togglingId === popup.popupId ? "처리 중..." : faded ? "노출하기" : "숨기기"}
          </Button>
          <button type="button" aria-label={`${popup.title} 수정`} className="rounded-button p-2 text-muted hover:bg-page hover:text-ink" onClick={() => openEditDialog(popup)}>
            <Pencil size={16} />
          </button>
          <button type="button" aria-label={`${popup.title} 삭제`} disabled={deletingId === popup.popupId} className="rounded-button p-2 text-muted hover:bg-page hover:text-primary-strong disabled:cursor-not-allowed disabled:opacity-50" onClick={() => handleDelete(popup)}>
            <Trash2 size={16} />
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader
        eyebrow="최고 관리자"
        title="팝업 관리"
        description="메인페이지 진입 시 뜨는 팝업을 등록해요. 이미지 또는 텍스트(부제)만으로도 만들 수 있고, 노출 기간을 비워두면 즉시 시작·무기한 노출돼요."
        action={<Button onClick={openCreateDialog}><Plus size={16} />팝업 추가</Button>}
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

      {loading && <div className="surface grid min-h-40 place-items-center text-sm text-muted">불러오는 중이에요...</div>}

      {!loading && !loadError && popups && popups.length === 0 && (
        <EmptyState title="등록된 팝업이 없어요" description="팝업 추가 버튼을 눌러 첫 팝업을 등록해 보세요." />
      )}

      {!loading && !loadError && popups && popups.length > 0 && (
        <div className="space-y-8">
          <section>
            <h2 className="mb-3 text-sm font-bold text-ink">노출 중인 팝업 ({activePopups.length})</h2>
            {activePopups.length === 0 ? (
              <div className="surface p-6 text-sm text-muted">노출 중인 팝업이 없어요.</div>
            ) : (
              <div className="space-y-2">{activePopups.map((popup) => renderRow(popup, false))}</div>
            )}
          </section>

          {inactivePopups.length > 0 && (
            <section>
              <h2 className="mb-3 text-sm font-bold text-ink">비노출 팝업 ({inactivePopups.length})</h2>
              <div className="space-y-2">{inactivePopups.map((popup) => renderRow(popup, true))}</div>
            </section>
          )}
        </div>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} title={editingPopup ? "팝업 수정" : "팝업 추가"}>
        <form onSubmit={handleSubmit} className="max-h-[70vh] space-y-4 overflow-y-auto pr-1">
          {formErrors.length > 0 && (
            <ul className="space-y-1 text-sm font-bold text-primary-strong">
              {formErrors.map((message) => <li key={message}>{message}</li>)}
            </ul>
          )}

          <div>
            {label("제목", true)}
            <Input value={form.title} onChange={(event) => update("title", event.target.value)} placeholder="예: 8월 정기 점검 안내" required />
          </div>
          <div>
            {label("부제 (이미지가 없으면 필수)")}
            <Textarea value={form.subtitle} onChange={(event) => update("subtitle", event.target.value)} placeholder="이미지 없이 텍스트만으로 구성할 때 여기에 본문을 적어요." />
          </div>

          <ImageUploadField
            label="팝업 이미지 (선택)"
            initialImageUrl={existingImageUrl}
            onObjectKeyChange={handleImageObjectKeyChange}
            onUploadingChange={setImageUploading}
            removable
            onRemove={handleImageRemove}
          />

          <div className="grid grid-cols-2 gap-3">
            <div>
              {label("너비(px)")}
              <Input type="number" min={1} value={form.width} onChange={(event) => update("width", event.target.value)} placeholder="예: 360" />
            </div>
            <div>
              {label("높이(px)")}
              <Input type="number" min={1} value={form.height} onChange={(event) => update("height", event.target.value)} placeholder="예: 480" />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              {label("노출 시작일시")}
              <Input type="datetime-local" value={form.startedAt} onChange={(event) => update("startedAt", event.target.value)} />
            </div>
            <div>
              {label("노출 종료일시")}
              <Input type="datetime-local" value={form.endedAt} onChange={(event) => update("endedAt", event.target.value)} />
            </div>
          </div>
          <p className="text-xs text-muted">비워두면 즉시 시작하고, 무기한 노출돼요.</p>

          <div>
            {label("배경색")}
            <div className="flex items-center gap-2">
              <input
                type="color"
                value={/^#[0-9a-fA-F]{6}$/.test(form.bgColor) ? form.bgColor : "#FBD9BD"}
                onChange={(event) => update("bgColor", event.target.value)}
                className="h-11 w-14 shrink-0 cursor-pointer rounded-button border border-line bg-card"
                aria-label="배경색 선택"
              />
              <Input value={form.bgColor} onChange={(event) => update("bgColor", event.target.value)} placeholder="#FBD9BD (비우면 기본색)" className="flex-1" />
            </div>
          </div>

          <div className="border-t border-line pt-4">
            <p className="mb-3 text-sm font-bold text-ink">버튼 (선택)</p>
            <div className="space-y-3">
              <div>
                {label("버튼 문구")}
                <Input value={form.linkLabel} onChange={(event) => update("linkLabel", event.target.value)} placeholder="예: 자세히 보기" />
              </div>
              <div className="grid grid-cols-[1fr_auto] gap-3">
                <div>
                  {label("이동 링크")}
                  <Input value={form.linkUrl} onChange={(event) => update("linkUrl", event.target.value)} placeholder="/notices/123 또는 https://..." />
                </div>
                <div>
                  {label("이동 방식")}
                  <Select value={form.linkTarget} onChange={(event) => update("linkTarget", event.target.value as LinkTarget)}>
                    <option value="SELF">현재 탭</option>
                    <option value="BLANK">새 탭</option>
                  </Select>
                </div>
              </div>
            </div>
          </div>

          <div className="flex justify-end gap-2 border-t border-line pt-4">
            <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>취소</Button>
            <Button type="submit" disabled={saving || imageUploading}>
              {saving ? "저장 중..." : imageUploading ? "이미지 업로드 중..." : "저장"}
            </Button>
          </div>
        </form>
      </Dialog>
      {confirmDialog}
    </div>
  );
}
