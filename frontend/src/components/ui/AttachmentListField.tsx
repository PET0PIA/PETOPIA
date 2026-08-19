import { FileText, Loader2, Paperclip, X } from "lucide-react";
import { useRef, useState, type ChangeEvent } from "react";
import { uploadDocument, validateDocumentFile } from "../../api/files";
import { formatFileSize } from "../../utils/fileSize";

/** 목록의 한 줄. 이미 저장된 첨부는 attachmentId를, 새로 올린 파일은 objectKey를 갖는다. */
export interface AttachmentItem {
  attachmentId?: number;
  objectKey?: string;
  originalName: string;
  fileSize: number;
}

interface AttachmentListFieldProps {
  label: string;
  items: AttachmentItem[];
  onChange: (items: AttachmentItem[]) => void;
  /** 업로드 중에는 호출부가 저장 버튼을 막아야 한다(ImageUploadField와 같은 규약). */
  onUploadingChange?: (uploading: boolean) => void;
  maxCount?: number;
  disabled?: boolean;
}

/**
 * 첨부파일 여러 개를 다루는 필드. {@link AttachmentUploadField}는 한 개 전용이라,
 * 목록으로 붙였다 뗐다 해야 하는 화면(공지 등)은 이쪽을 쓴다.
 *
 * <p>파일을 고르면 곧바로 S3 임시 영역에 올리고 objectKey를 목록에 담아둔다. 실제 확정은
 * 호출부가 도메인 저장 API를 호출할 때 서버가 한다 - 저장하지 않고 창을 닫으면 임시 파일로 남는다.
 */
export function AttachmentListField({
  label, items, onChange, onUploadingChange, maxCount = 5, disabled = false,
}: AttachmentListFieldProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploadingName, setUploadingName] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const full = items.length >= maxCount;

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    if (full) {
      setError(`첨부파일은 최대 ${maxCount}개까지 올릴 수 있어요.`);
      return;
    }
    const validationError = validateDocumentFile(file);
    if (validationError) {
      setError(validationError);
      return;
    }

    setError(null);
    setUploadingName(file.name);
    onUploadingChange?.(true);
    try {
      const objectKey = await uploadDocument(file);
      onChange([...items, { objectKey, originalName: file.name, fileSize: file.size }]);
    } catch {
      setError("파일 업로드에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setUploadingName(null);
      onUploadingChange?.(false);
    }
  }

  function handleRemove(index: number) {
    onChange(items.filter((_, i) => i !== index));
  }

  return (
    <div>
      <span className="mb-1.5 block text-sm font-bold text-ink">
        {label} <span className="font-medium text-muted">({items.length}/{maxCount})</span>
      </span>

      {items.length > 0 && (
        <ul className="mb-2 space-y-1.5">
          {items.map((item, index) => (
            <li key={item.attachmentId ?? item.objectKey} className="flex items-center gap-2 rounded-button bg-surface-alt px-3 py-2">
              <FileText size={16} className="shrink-0 text-muted" />
              <span className="min-w-0 flex-1 truncate text-sm text-ink">{item.originalName}</span>
              <span className="shrink-0 text-xs text-muted">{formatFileSize(item.fileSize)}</span>
              <button
                type="button"
                onClick={() => handleRemove(index)}
                disabled={disabled}
                aria-label={`${item.originalName} 첨부 제거`}
                className="shrink-0 rounded-button p-1 text-muted hover:text-primary-strong disabled:cursor-not-allowed disabled:opacity-50"
              >
                <X size={14} />
              </button>
            </li>
          ))}
        </ul>
      )}

      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={disabled || full || uploadingName !== null}
        className="flex items-center gap-2 rounded-button border border-dashed border-line px-3 py-2 text-sm text-muted hover:bg-surface-alt hover:text-ink disabled:cursor-not-allowed disabled:opacity-60"
      >
        {uploadingName ? <Loader2 size={16} className="animate-spin" /> : <Paperclip size={16} />}
        {uploadingName ? `${uploadingName} 올리는 중...` : full ? `최대 ${maxCount}개까지 첨부했어요` : "파일 첨부"}
      </button>
      <p className="mt-1 text-xs text-muted">pdf, docx, xlsx, pptx · 50MB 이하</p>

      <input ref={inputRef} type="file" accept=".pdf,.docx,.xlsx,.pptx" className="hidden" onChange={handleFileChange} />
      {error && <p className="mt-1.5 text-xs font-bold text-primary-strong">{error}</p>}
    </div>
  );
}
