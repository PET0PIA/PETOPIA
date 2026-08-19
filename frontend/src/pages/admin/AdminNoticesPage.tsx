import { AlertCircle, Paperclip, Pencil, Pin, Plus, Trash2 } from "lucide-react";
import { Suspense, lazy, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { PageHeader } from "../../components/common/PageHeader";
import { EmptyState } from "../../components/common/EmptyState";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { Input } from "../../components/ui/Input";
import { Select } from "../../components/ui/Select";
import { AttachmentListField, type AttachmentItem } from "../../components/ui/AttachmentListField";
import { useConfirm } from "../../components/ui/useConfirm";
import { ApiError } from "../../api/client";
import { uploadImage } from "../../api/files";
import { getFairApplications, type FairApplicationSummary } from "../../api/fair";
import { formatShortDate } from "../../utils/date";
import {
  EDITABLE_NOTICE_CATEGORIES,
  NOTICE_CATEGORY_LABELS,
  confirmNoticeImage,
  createNotice,
  deleteNotice,
  getAdminNotices,
  setNoticePinned,
  setNoticePublished,
  updateNotice,
  type AdminNotice,
  type NoticeCategory,
} from "../../api/notice";

/**
 * 에디터(Tiptap)는 용량이 큰 편이라, 글쓰기 창을 열 때만 내려받는다. 이렇게 하지 않으면
 * 홈만 보러 온 방문자도 관리자용 편집기를 함께 내려받게 된다.
 */
const RichTextEditor = lazy(() =>
  import("../../components/ui/RichTextEditor").then((module) => ({ default: module.RichTextEditor })),
);

interface FormState {
  category: NoticeCategory;
  title: string;
  content: string;
  fairId: string;
  pinned: boolean;
  published: boolean;
}

const emptyForm: FormState = {
  category: "NOTICE",
  title: "",
  content: "",
  fairId: "",
  pinned: false,
  published: true,
};

function label(text: string, required = false) {
  return (
    <span className="mb-1.5 block text-sm font-bold text-ink">
      {text}
      {required && <span className="ml-1 text-primary-strong">*</span>}
    </span>
  );
}

export function AdminNoticesPage() {
  const { confirm, confirmDialog } = useConfirm();

  const [notices, setNotices] = useState<AdminNotice[] | null>(null);
  const [fairs, setFairs] = useState<FairApplicationSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingNotice, setEditingNotice] = useState<AdminNotice | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [attachments, setAttachments] = useState<AttachmentItem[]>([]);
  const [uploading, setUploading] = useState(false);
  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);

  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  // 연속으로 토글할 때 늦게 도착한 예전 응답이 최신 목록을 덮어쓰지 않게 한다(팝업 관리와 동일).
  const fetchRequestIdRef = useRef(0);

  function fetchNotices() {
    const requestId = ++fetchRequestIdRef.current;
    getAdminNotices()
      .then((data) => {
        if (fetchRequestIdRef.current !== requestId) return;
        setLoadError(null);
        setNotices(data);
      })
      .catch((error) => {
        if (fetchRequestIdRef.current !== requestId) return;
        setNotices(null);
        setLoadError(error instanceof ApiError ? error.message : "공지 목록을 불러오지 못했어요.");
      })
      .finally(() => {
        if (fetchRequestIdRef.current === requestId) setLoading(false);
      });
  }

  useEffect(fetchNotices, []);

  // 행사 연결 선택기용. 실패해도 공지 관리 자체는 되므로 조용히 빈 목록으로 둔다.
  useEffect(() => {
    let ignore = false;
    getFairApplications()
      .then((data) => { if (!ignore) setFairs(data.filter((fair) => fair.canceledAt === null)); })
      .catch(() => { if (!ignore) setFairs([]); });
    return () => { ignore = true; };
  }, []);

  const publishedCount = useMemo(() => (notices ?? []).filter((notice) => notice.published).length, [notices]);

  function openCreateDialog() {
    setEditingNotice(null);
    setForm(emptyForm);
    setAttachments([]);
    setUploading(false);
    setFormErrors([]);
    setDialogOpen(true);
  }

  function openEditDialog(notice: AdminNotice) {
    setEditingNotice(notice);
    setForm({
      // 모집공고는 이 화면에서 만들 수 없으니, 방어적으로 공지로 되돌린다.
      category: notice.category === "RECRUIT" ? "NOTICE" : notice.category,
      title: notice.title,
      content: notice.content,
      fairId: notice.fairId ? String(notice.fairId) : "",
      pinned: notice.pinned,
      published: notice.published,
    });
    setAttachments(notice.attachments.map((attachment) => ({
      attachmentId: attachment.attachmentId,
      originalName: attachment.originalName,
      fileSize: attachment.fileSize,
    })));
    setUploading(false);
    setFormErrors([]);
    setDialogOpen(true);
  }

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }

  // 저장·업로드가 끝나기 전에 닫으면, 늦게 끝난 요청이 다음에 연 폼을 덮어쓸 수 있다.
  function closeDialog() {
    if (saving || uploading) return;
    setDialogOpen(false);
  }

  /** 본문에 넣을 이미지: 임시 업로드 → 서버 확정 → 바로 쓸 수 있는 URL. */
  async function handleUploadContentImage(file: File): Promise<string> {
    const objectKey = await uploadImage(file);
    return confirmNoticeImage(objectKey);
  }

  function validate(): string[] {
    const errors: string[] = [];
    if (form.title.trim() === "") errors.push("제목을 입력해 주세요.");
    if (form.content.trim() === "") errors.push("본문을 입력해 주세요.");
    if (uploading) errors.push("파일 업로드가 끝날 때까지 잠시만 기다려 주세요.");
    return errors;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validate();
    setFormErrors(errors);
    if (errors.length > 0) return;

    setSaving(true);
    try {
      const payload = {
        category: form.category,
        title: form.title.trim(),
        content: form.content,
        fairId: form.fairId === "" ? null : Number(form.fairId),
        pinned: form.pinned,
        published: form.published,
        attachments: attachments.map((item) => (
          item.attachmentId
            ? { attachmentId: item.attachmentId }
            : { objectKey: item.objectKey, originalName: item.originalName, fileSize: item.fileSize }
        )),
      };
      if (editingNotice) {
        await updateNotice(editingNotice.noticeId, payload);
      } else {
        await createNotice(payload);
      }
      setDialogOpen(false);
      fetchNotices();
    } catch (error) {
      setFormErrors([error instanceof ApiError ? error.message : "공지를 저장하지 못했어요."]);
    } finally {
      setSaving(false);
    }
  }

  async function runRowAction(noticeId: number, action: () => Promise<void>, failMessage: string) {
    setActionError(null);
    setBusyId(noticeId);
    try {
      await action();
      fetchNotices();
    } catch (error) {
      setActionError(error instanceof ApiError ? error.message : failMessage);
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(notice: AdminNotice) {
    const attachmentNote = notice.attachments.length > 0 ? ` 첨부파일 ${notice.attachments.length}개도 함께 지워져요.` : "";
    if (!(await confirm({ description: `'${notice.title}' 공지를 삭제할까요?${attachmentNote}` }))) return;
    await runRowAction(notice.noticeId, () => deleteNotice(notice.noticeId), "공지를 삭제하지 못했어요.");
  }

  return (
    <div className="mx-auto max-w-5xl py-2">
      <PageHeader
        eyebrow="최고 관리자"
        title="공지사항 관리"
        description="헤더의 '소식·이벤트'와 홈 화면에 노출되는 공지를 등록해요. 행사별 참가업체 모집공고는 여기가 아니라 행사 담당자가 작성하고, 소식 목록에는 자동으로 함께 보여요."
        action={
          // 설명이 두 줄이라 헤더 flex가 버튼을 눌러 글자가 줄바꿈된다. 버튼은 줄어들지 않게 고정.
          <Button className="shrink-0 whitespace-nowrap" onClick={openCreateDialog}><Plus size={16} />공지 등록</Button>
        }
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

      {!loading && !loadError && notices && notices.length === 0 && (
        <EmptyState title="등록된 공지가 없어요" description="공지 등록 버튼을 눌러 첫 공지를 올려 보세요." />
      )}

      {!loading && !loadError && notices && notices.length > 0 && (
        <>
          <p className="mb-3 text-sm text-muted">전체 {notices.length}건 · 게시 중 {publishedCount}건</p>
          <div className="space-y-2">
            {notices.map((notice) => (
              <div key={notice.noticeId} className={`surface flex items-center gap-3 p-3 ${notice.published ? "" : "opacity-70"}`}>
                <Badge>{NOTICE_CATEGORY_LABELS[notice.category]}</Badge>
                <div className="min-w-0 flex-1">
                  <p className="flex items-center gap-1.5 truncate text-sm font-bold text-ink">
                    {notice.pinned && <Pin size={13} className="shrink-0" aria-label="상단 고정" />}
                    {notice.title}
                    {notice.attachments.length > 0 && (
                      <span className="flex shrink-0 items-center gap-0.5 text-xs font-medium text-muted">
                        <Paperclip size={12} />{notice.attachments.length}
                      </span>
                    )}
                  </p>
                  <p className="truncate text-xs text-muted">
                    {formatShortDate(notice.createdAt)} · 조회 {notice.viewCount}
                    {notice.fairName && ` · ${notice.fairName}`}
                    {!notice.published && " · 비공개"}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <Button
                    variant="outline"
                    disabled={busyId === notice.noticeId}
                    onClick={() => runRowAction(notice.noticeId, () => setNoticePinned(notice.noticeId, !notice.pinned), "고정 상태를 변경하지 못했어요.")}
                  >
                    {notice.pinned ? "고정 해제" : "상단 고정"}
                  </Button>
                  <Button
                    variant="outline"
                    disabled={busyId === notice.noticeId}
                    onClick={() => runRowAction(notice.noticeId, () => setNoticePublished(notice.noticeId, !notice.published), "게시 상태를 변경하지 못했어요.")}
                  >
                    {busyId === notice.noticeId ? "처리 중..." : notice.published ? "숨기기" : "게시하기"}
                  </Button>
                  <button type="button" aria-label={`${notice.title} 수정`} className="rounded-button p-2 text-muted hover:bg-page hover:text-ink" onClick={() => openEditDialog(notice)}>
                    <Pencil size={16} />
                  </button>
                  <button type="button" aria-label={`${notice.title} 삭제`} disabled={busyId === notice.noticeId} className="rounded-button p-2 text-muted hover:bg-page hover:text-primary-strong disabled:cursor-not-allowed disabled:opacity-50" onClick={() => handleDelete(notice)}>
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      <Dialog open={dialogOpen} onClose={closeDialog} title={editingNotice ? "공지 수정" : "공지 등록"}>
        <form onSubmit={handleSubmit} className="max-h-[70vh] space-y-4 overflow-y-auto pr-1">
          {formErrors.length > 0 && (
            <ul className="space-y-1 text-sm font-bold text-primary-strong">
              {formErrors.map((message) => <li key={message}>{message}</li>)}
            </ul>
          )}

          <div className="grid grid-cols-[140px_1fr] gap-3">
            <div>
              {label("분류", true)}
              <Select value={form.category} onChange={(event) => update("category", event.target.value as NoticeCategory)}>
                {EDITABLE_NOTICE_CATEGORIES.map((category) => (
                  <option key={category} value={category}>{NOTICE_CATEGORY_LABELS[category]}</option>
                ))}
              </Select>
            </div>
            <div>
              {label("제목", true)}
              <Input value={form.title} onChange={(event) => update("title", event.target.value)} placeholder="예: 2026 서울 펫페어 예매 안내" required />
            </div>
          </div>

          <div>
            {label("본문", true)}
            <Suspense fallback={<div className="grid min-h-56 place-items-center rounded-card ring-1 ring-line text-sm text-muted">편집기를 불러오는 중이에요...</div>}>
              <RichTextEditor
                value={form.content}
                onChange={(html) => update("content", html)}
                onUploadImage={handleUploadContentImage}
                onUploadingChange={setUploading}
                placeholder="공지 내용을 입력하세요. 사진은 도구모음의 사진 버튼으로 글 중간에 넣을 수 있어요."
                disabled={saving}
              />
            </Suspense>
          </div>

          <div>
            {label("행사 연결 (선택)")}
            <Select value={form.fairId} onChange={(event) => update("fairId", event.target.value)}>
              <option value="">연결 안 함 (전체 공지)</option>
              {fairs.map((fair) => (
                <option key={fair.fairId} value={fair.fairId}>{fair.name}</option>
              ))}
            </Select>
            <p className="mt-1 text-xs text-muted">특정 행사에 대한 공지면 연결해 주세요. 목록과 상세에 행사 이름이 함께 보여요.</p>
          </div>

          <AttachmentListField
            label="첨부파일"
            items={attachments}
            onChange={setAttachments}
            onUploadingChange={setUploading}
            disabled={saving}
          />

          <div className="space-y-2 border-t border-line pt-4">
            <label className="flex items-center gap-2 text-sm text-ink">
              <input type="checkbox" checked={form.pinned} onChange={(event) => update("pinned", event.target.checked)} className="size-4" />
              상단 고정 (목록 맨 위에 두고, 홈 화면 띠에도 흘려요)
            </label>
            <label className="flex items-center gap-2 text-sm text-ink">
              <input type="checkbox" checked={form.published} onChange={(event) => update("published", event.target.checked)} className="size-4" />
              바로 게시 (해제하면 관리자에게만 보이는 상태로 저장돼요)
            </label>
          </div>

          <div className="flex justify-end gap-2 border-t border-line pt-4">
            <Button type="button" variant="outline" onClick={closeDialog} disabled={saving || uploading}>취소</Button>
            <Button type="submit" disabled={saving || uploading}>
              {saving ? "저장 중..." : uploading ? "업로드 중..." : "저장"}
            </Button>
          </div>
        </form>
      </Dialog>
      {confirmDialog}
    </div>
  );
}
