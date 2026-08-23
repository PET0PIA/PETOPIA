import { AlertCircle, ChevronDown, ChevronUp, GripVertical, Pencil, Plus, Trash2 } from "lucide-react";
import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { ImageUploadField } from "../../components/ui/ImageUploadField";
import { Input } from "../../components/ui/Input";
import { Select } from "../../components/ui/Select";
import { Textarea } from "../../components/ui/Textarea";
import { useConfirm } from "../../components/ui/useConfirm";
import { ApiError } from "../../api/client";
import {
  createBanner,
  deleteBanner,
  getAdminBanners,
  toggleBannerActive,
  updateBanner,
  updateBannerOrder,
  type Banner,
  type BannerCreateInput,
  type LinkTarget,
} from "../../api/banner";

interface FormState {
  title: string;
  eyebrow: string;
  subtitle: string;
  linkUrl: string;
  linkTarget: LinkTarget;
  linkLabel: string;
  link2Url: string;
  link2Target: LinkTarget;
  link2Label: string;
  bgColor: string;
  startedAt: string;
  endedAt: string;
}

const emptyForm: FormState = {
  title: "",
  eyebrow: "",
  subtitle: "",
  linkUrl: "",
  linkTarget: "SELF",
  linkLabel: "",
  link2Url: "",
  link2Target: "SELF",
  link2Label: "",
  bgColor: "",
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

function toDatetimeLocal(value: string): string {
  return value.length >= 16 ? value.slice(0, 16) : value;
}

function toIsoDateTime(value: string): string {
  return value.length === 16 ? `${value}:00` : value;
}

function defaultPeriod(): { startedAt: string; endedAt: string } {
  const now = new Date();
  const in30Days = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000);
  const toLocal = (date: Date) => {
    const offset = date.getTimezoneOffset();
    return new Date(date.getTime() - offset * 60 * 1000).toISOString().slice(0, 16);
  };
  return { startedAt: toLocal(now), endedAt: toLocal(in30Days) };
}

function formatDateTime(value: string): string {
  return value.slice(0, 16).replace("T", " ");
}

export function AdminBannersPage() {
  const { confirm, confirmDialog } = useConfirm();

  const [banners, setBanners] = useState<Banner[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingBanner, setEditingBanner] = useState<Banner | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [existingImageUrl, setExistingImageUrl] = useState<string | null>(null);
  const [imageObjectKey, setImageObjectKey] = useState<string | null>(null);
  const [imageUploading, setImageUploading] = useState(false);
  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);

  const [togglingId, setTogglingId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // 드래그로 바꾼 노출 중 배너 순서(서버에 아직 저장 안 한 로컬 상태). 배너 목록을 새로 불러올 때마다 초기화된다.
  const [activeOrder, setActiveOrder] = useState<Banner[]>([]);
  const dragIndexRef = useRef<number | null>(null);
  const [savingOrder, setSavingOrder] = useState(false);

  function fetchBanners() {
    getAdminBanners()
      .then((data) => {
        setLoadError(null);
        setBanners(data);
        setActiveOrder(data.filter((b) => b.active).sort((a, b) => a.sortOrder - b.sortOrder));
      })
      .catch((error) => {
        setBanners(null);
        setLoadError(error instanceof ApiError ? error.message : "배너 목록을 불러오지 못했어요.");
      })
      .finally(() => setLoading(false));
  }

  useEffect(fetchBanners, []);

  const inactiveBanners = useMemo(
    () => (banners ?? []).filter((b) => !b.active).sort((a, b) => a.sortOrder - b.sortOrder),
    [banners]
  );
  const orderDirty = useMemo(() => {
    const original = (banners ?? []).filter((b) => b.active).sort((a, b) => a.sortOrder - b.sortOrder);
    return original.length === activeOrder.length && original.some((b, i) => b.bannerId !== activeOrder[i]?.bannerId);
  }, [banners, activeOrder]);

  function openCreateDialog() {
    setEditingBanner(null);
    setForm({ ...emptyForm, ...defaultPeriod() });
    setExistingImageUrl(null);
    setImageObjectKey(null);
    setImageUploading(false);
    setFormErrors([]);
    setDialogOpen(true);
  }

  function openEditDialog(banner: Banner) {
    setEditingBanner(banner);
    setForm({
      title: banner.title,
      eyebrow: banner.eyebrow ?? "",
      subtitle: banner.subtitle ?? "",
      linkUrl: banner.linkUrl ?? "",
      linkTarget: banner.linkTarget,
      linkLabel: banner.linkLabel ?? "",
      link2Url: banner.link2Url ?? "",
      link2Target: banner.link2Target ?? "SELF",
      link2Label: banner.link2Label ?? "",
      bgColor: banner.bgColor ?? "",
      startedAt: toDatetimeLocal(banner.startedAt ?? ""),
      endedAt: toDatetimeLocal(banner.endedAt ?? ""),
    });
    setExistingImageUrl(banner.imageKey);
    setImageObjectKey(null);
    setImageUploading(false);
    setFormErrors([]);
    setDialogOpen(true);
  }

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }

  function validate(): string[] {
    const errors: string[] = [];
    if (form.title.trim() === "") errors.push("제목을 입력해 주세요.");
    if (!editingBanner && !imageObjectKey) errors.push("이미지를 선택해 주세요.");
    if (form.startedAt === "" || form.endedAt === "") errors.push("노출 시작/종료 일시를 입력해 주세요.");
    if (form.startedAt !== "" && form.endedAt !== "" && form.startedAt >= form.endedAt) {
      errors.push("노출 종료 일시는 시작 일시보다 뒤여야 해요.");
    }
    if (form.linkLabel.trim() !== "" && form.linkUrl.trim() === "") errors.push("주 버튼 문구를 넣으려면 이동 링크도 함께 입력해 주세요.");
    const hasLink2Label = form.link2Label.trim() !== "";
    const hasLink2Url = form.link2Url.trim() !== "";
    if (hasLink2Label !== hasLink2Url) errors.push("보조 버튼은 문구와 이동 링크를 둘 다 입력하거나 둘 다 비워주세요.");
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
      if (editingBanner) {
        await updateBanner(editingBanner.bannerId, {
          title: form.title.trim(),
          eyebrow: form.eyebrow.trim(),
          subtitle: form.subtitle.trim(),
          imageKey: imageObjectKey ?? undefined,
          linkUrl: form.linkUrl.trim(),
          linkTarget: form.linkTarget,
          linkLabel: form.linkLabel.trim(),
          link2Label: form.link2Label.trim(),
          link2Url: form.link2Url.trim(),
          link2Target: form.link2Target,
          bgColor: form.bgColor.trim(),
          startedAt: toIsoDateTime(form.startedAt),
          endedAt: toIsoDateTime(form.endedAt),
        });
      } else {
        // 비노출 배너가 이미 쓰고 있는 sortOrder와 겹치지 않도록, 활성 배너 개수가 아니라
        // 현재 존재하는 모든 배너의 최댓값+1로 매긴다(나중에 그 배너가 다시 노출돼도 충돌 안 함).
        const nextSortOrder = (banners ?? []).reduce((max, b) => Math.max(max, b.sortOrder), -1) + 1;
        const payload: BannerCreateInput = {
          title: form.title.trim(),
          eyebrow: form.eyebrow.trim() || undefined,
          subtitle: form.subtitle.trim() || undefined,
          imageKey: imageObjectKey!,
          linkUrl: form.linkUrl.trim() || undefined,
          linkTarget: form.linkTarget,
          linkLabel: form.linkLabel.trim() || undefined,
          link2Label: form.link2Label.trim() || undefined,
          link2Url: form.link2Url.trim() || undefined,
          link2Target: form.link2Label.trim() ? form.link2Target : undefined,
          bgColor: form.bgColor.trim() || undefined,
          sortOrder: nextSortOrder,
          startedAt: toIsoDateTime(form.startedAt),
          endedAt: toIsoDateTime(form.endedAt),
        };
        await createBanner(payload);
      }
      setDialogOpen(false);
      fetchBanners();
    } catch (error) {
      setFormErrors([error instanceof ApiError ? error.message : "배너를 저장하지 못했어요."]);
    } finally {
      setSaving(false);
    }
  }

  async function handleToggle(banner: Banner) {
    setActionError(null);
    setTogglingId(banner.bannerId);
    try {
      await toggleBannerActive(banner.bannerId, !banner.active);
      fetchBanners();
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : "노출 상태를 변경하지 못했어요.");
    } finally {
      setTogglingId(null);
    }
  }

  async function handleDelete(banner: Banner) {
    if (!(await confirm({ description: `'${banner.title.split("\n")[0]}' 배너를 삭제할까요?` }))) return;
    setActionError(null);
    setDeletingId(banner.bannerId);
    try {
      await deleteBanner(banner.bannerId);
      fetchBanners();
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : "배너를 삭제하지 못했어요.");
    } finally {
      setDeletingId(null);
    }
  }

  function handleDragStart(index: number) {
    dragIndexRef.current = index;
  }

  function handleDragOver(event: React.DragEvent<HTMLDivElement>) {
    event.preventDefault();
  }

  function handleDrop(index: number) {
    const from = dragIndexRef.current;
    dragIndexRef.current = null;
    if (from === null || from === index) return;
    setActiveOrder((previous) => {
      const next = [...previous];
      const [moved] = next.splice(from, 1);
      next.splice(index, 0, moved);
      return next;
    });
  }

  // 드래그는 마우스로만 가능해서, 키보드·스크린리더로도 순서를 바꿀 수 있게 위/아래 버튼을 함께 둔다.
  function moveActiveBanner(index: number, direction: -1 | 1) {
    setActiveOrder((previous) => {
      const target = index + direction;
      if (target < 0 || target >= previous.length) return previous;
      const next = [...previous];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  async function handleSaveOrder() {
    setActionError(null);
    setSavingOrder(true);
    try {
      await updateBannerOrder(activeOrder.map((b) => b.bannerId));
      fetchBanners();
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : "순서를 저장하지 못했어요.");
    } finally {
      setSavingOrder(false);
    }
  }

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader
        eyebrow="최고 관리자"
        title="배너 관리"
        description="메인페이지 히어로 배너를 등록하고, 드래그로 노출 순서를 바꿔요. 노출 시작/종료 기간을 벗어나면 활성 상태여도 공개 화면에서 빠져요."
        action={<Button onClick={openCreateDialog}><Plus size={16} />배너 추가</Button>}
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

      {!loading && !loadError && banners && banners.length === 0 && (
        <EmptyState title="등록된 배너가 없어요" description="배너 추가 버튼을 눌러 첫 배너를 등록해 보세요." />
      )}

      {!loading && !loadError && banners && banners.length > 0 && (
        <div className="space-y-8">
          <section>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-sm font-bold text-ink">노출 중인 배너 ({activeOrder.length})</h2>
              {orderDirty && (
                <Button onClick={handleSaveOrder} disabled={savingOrder}>
                  {savingOrder ? "저장 중..." : "순서 저장"}
                </Button>
              )}
            </div>
            {activeOrder.length === 0 ? (
              <Card className="p-6 text-sm text-muted">노출 중인 배너가 없어요.</Card>
            ) : (
              <div className="space-y-2">
                {activeOrder.map((banner, index) => (
                  <div
                    key={banner.bannerId}
                    draggable
                    onDragStart={() => handleDragStart(index)}
                    onDragOver={handleDragOver}
                    onDrop={() => handleDrop(index)}
                    className="surface flex items-center gap-3 p-3"
                  >
                    <span className="cursor-grab text-muted" aria-hidden="true"><GripVertical size={18} /></span>
                    <div className="flex shrink-0 flex-col">
                      <button
                        type="button"
                        aria-label={`${banner.title.split("\n")[0]} 위로 이동`}
                        disabled={index === 0}
                        onClick={() => moveActiveBanner(index, -1)}
                        className="rounded-button p-1 text-muted hover:bg-page hover:text-ink disabled:cursor-not-allowed disabled:opacity-30"
                      >
                        <ChevronUp size={16} />
                      </button>
                      <button
                        type="button"
                        aria-label={`${banner.title.split("\n")[0]} 아래로 이동`}
                        disabled={index === activeOrder.length - 1}
                        onClick={() => moveActiveBanner(index, 1)}
                        className="rounded-button p-1 text-muted hover:bg-page hover:text-ink disabled:cursor-not-allowed disabled:opacity-30"
                      >
                        <ChevronDown size={16} />
                      </button>
                    </div>
                    <img src={banner.imageKey} alt="" className="h-12 w-20 shrink-0 rounded-button object-cover" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-bold text-ink">{banner.title.split("\n")[0]}</p>
                      <p className="truncate text-xs text-muted">
                        {formatDateTime(banner.startedAt ?? "")} ~ {formatDateTime(banner.endedAt ?? "")}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <Button variant="outline" disabled={togglingId === banner.bannerId} onClick={() => handleToggle(banner)}>
                        {togglingId === banner.bannerId ? "처리 중..." : "숨기기"}
                      </Button>
                      <button type="button" aria-label={`${banner.title} 수정`} className="rounded-button p-2 text-muted hover:bg-page hover:text-ink" onClick={() => openEditDialog(banner)}>
                        <Pencil size={16} />
                      </button>
                      <button type="button" aria-label={`${banner.title} 삭제`} disabled={deletingId === banner.bannerId} className="rounded-button p-2 text-muted hover:bg-page hover:text-primary-strong disabled:cursor-not-allowed disabled:opacity-50" onClick={() => handleDelete(banner)}>
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>

          {inactiveBanners.length > 0 && (
            <section>
              <h2 className="mb-3 text-sm font-bold text-ink">비노출 배너 ({inactiveBanners.length})</h2>
              <div className="space-y-2">
                {inactiveBanners.map((banner) => (
                  <div key={banner.bannerId} className="surface flex items-center gap-3 p-3 opacity-70">
                    <img src={banner.imageKey} alt="" className="h-12 w-20 shrink-0 rounded-button object-cover" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-bold text-ink">{banner.title.split("\n")[0]}</p>
                      <Badge tone="neutral">비노출</Badge>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <Button variant="outline" disabled={togglingId === banner.bannerId} onClick={() => handleToggle(banner)}>
                        {togglingId === banner.bannerId ? "처리 중..." : "노출하기"}
                      </Button>
                      <button type="button" aria-label={`${banner.title} 수정`} className="rounded-button p-2 text-muted hover:bg-page hover:text-ink" onClick={() => openEditDialog(banner)}>
                        <Pencil size={16} />
                      </button>
                      <button type="button" aria-label={`${banner.title} 삭제`} disabled={deletingId === banner.bannerId} className="rounded-button p-2 text-muted hover:bg-page hover:text-primary-strong disabled:cursor-not-allowed disabled:opacity-50" onClick={() => handleDelete(banner)}>
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} title={editingBanner ? "배너 수정" : "배너 추가"}>
        <form onSubmit={handleSubmit} className="max-h-[70vh] space-y-4 overflow-y-auto pr-1">
          {formErrors.length > 0 && (
            <ul className="space-y-1 text-sm font-bold text-primary-strong">
              {formErrors.map((message) => <li key={message}>{message}</li>)}
            </ul>
          )}

          <div>
            {label("제목", true)}
            <Textarea value={form.title} onChange={(event) => update("title", event.target.value)} placeholder={"예: 2026 서울 펫페어\n지금 예약하세요 (줄바꿈하면 두 줄 헤드라인이 돼요)"} required />
          </div>
          <div>
            {label("상단 라벨 (eyebrow)")}
            <Input value={form.eyebrow} onChange={(event) => update("eyebrow", event.target.value)} placeholder="예: 예약 오픈" />
          </div>
          <div>
            {label("부제")}
            <Textarea value={form.subtitle} onChange={(event) => update("subtitle", event.target.value)} placeholder="예: 2026. 09. 18 - 09. 20 · 서울 코엑스 C홀" />
          </div>

          <ImageUploadField
            label="배너 이미지"
            initialImageUrl={existingImageUrl}
            onObjectKeyChange={setImageObjectKey}
            onUploadingChange={setImageUploading}
          />

          <div className="grid grid-cols-2 gap-3">
            <div>
              {label("노출 시작일시", true)}
              <Input type="datetime-local" value={form.startedAt} onChange={(event) => update("startedAt", event.target.value)} required />
            </div>
            <div>
              {label("노출 종료일시", true)}
              <Input type="datetime-local" value={form.endedAt} onChange={(event) => update("endedAt", event.target.value)} required />
            </div>
          </div>

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
            <p className="mb-3 text-sm font-bold text-ink">주 버튼</p>
            <div className="space-y-3">
              <div>
                {label("버튼 문구")}
                <Input value={form.linkLabel} onChange={(event) => update("linkLabel", event.target.value)} placeholder="예: 예약하러 가기" />
              </div>
              <div className="grid grid-cols-[1fr_auto] gap-3">
                <div>
                  {label("이동 링크")}
                  <Input value={form.linkUrl} onChange={(event) => update("linkUrl", event.target.value)} placeholder="/fairs/upcoming 또는 https://..." />
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

          <div className="border-t border-line pt-4">
            <p className="mb-3 text-sm font-bold text-ink">보조 버튼 (선택)</p>
            <div className="space-y-3">
              <div>
                {label("버튼 문구")}
                <Input value={form.link2Label} onChange={(event) => update("link2Label", event.target.value)} placeholder="예: 티켓 예약하기" />
              </div>
              <div className="grid grid-cols-[1fr_auto] gap-3">
                <div>
                  {label("이동 링크")}
                  <Input value={form.link2Url} onChange={(event) => update("link2Url", event.target.value)} placeholder="/tickets 또는 https://..." />
                </div>
                <div>
                  {label("이동 방식")}
                  <Select value={form.link2Target} onChange={(event) => update("link2Target", event.target.value as LinkTarget)}>
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
