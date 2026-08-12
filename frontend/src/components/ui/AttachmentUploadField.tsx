import { FileText, Loader2, Paperclip, X } from "lucide-react";
import { useRef, useState, type ChangeEvent } from "react";
import { uploadDocument, validateDocumentFile } from "../../api/files";

interface AttachmentUploadFieldProps {
  label: string;
  /** ImageUploadField와 동일한 이유 — 업로드 중에는 호출부가 제출 버튼을 막아야 한다. */
  onObjectKeyChange: (objectKey: string | null) => void;
  onUploadingChange?: (uploading: boolean) => void;
  disabled?: boolean;
}

/**
 * S3 presigned 업로드 흐름을 감싼 첨부파일 필드(문서 전용). ImageUploadField와 구조는
 * 동일하고, 이미지 미리보기 대신 파일명만 보여준다는 점만 다르다.
 */
export function AttachmentUploadField({ label, onObjectKeyChange, onUploadingChange, disabled }: AttachmentUploadFieldProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [fileName, setFileName] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleRemove() {
    setFileName(null);
    setError(null);
    onObjectKeyChange(null);
  }

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    const validationError = validateDocumentFile(file);
    if (validationError) {
      setError(validationError);
      return;
    }

    setFileName(file.name);
    setError(null);
    setUploading(true);
    onUploadingChange?.(true);
    onObjectKeyChange(null);
    try {
      const objectKey = await uploadDocument(file);
      onObjectKeyChange(objectKey);
    } catch {
      setError("파일 업로드에 실패했어요. 잠시 후 다시 시도해 주세요.");
      setFileName(null);
    } finally {
      setUploading(false);
      onUploadingChange?.(false);
    }
  }

  return (
    <div>
      <span className="mb-1.5 block text-sm font-bold text-ink">{label}</span>
      <div className="flex items-center gap-4">
        <button
          type="button"
          disabled={disabled || uploading}
          onClick={() => inputRef.current?.click()}
          className="relative grid size-11 shrink-0 place-items-center overflow-hidden rounded-button border border-dashed border-line bg-page text-muted hover:bg-card disabled:cursor-not-allowed disabled:opacity-60"
        >
          {fileName ? <FileText size={18} /> : <Paperclip size={18} />}
          {uploading && (
            <span className="absolute inset-0 grid place-items-center bg-black/40 text-white">
              <Loader2 size={16} className="animate-spin" />
            </span>
          )}
        </button>
        <div className="flex flex-col gap-1">
          <button
            type="button"
            disabled={disabled || uploading}
            onClick={() => inputRef.current?.click()}
            className="text-left text-sm font-bold text-ink hover:underline disabled:cursor-not-allowed disabled:opacity-60"
          >
            {fileName ?? "파일 선택"}
          </button>
          {fileName && (
            <button
              type="button"
              disabled={disabled || uploading}
              onClick={handleRemove}
              className="inline-flex items-center gap-1 text-left text-sm font-bold text-primary-strong hover:underline disabled:cursor-not-allowed disabled:opacity-60"
            >
              <X size={14} />삭제
            </button>
          )}
          <span className="text-xs text-muted">pdf, docx, xlsx, pptx / 최대 50MB</span>
          {error && <span className="text-xs font-bold text-primary-strong">{error}</span>}
        </div>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept=".pdf,.docx,.xlsx,.pptx"
        className="hidden"
        onChange={handleFileChange}
      />
    </div>
  );
}