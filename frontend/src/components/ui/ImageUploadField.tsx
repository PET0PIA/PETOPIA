import { ImagePlus, Loader2, X } from "lucide-react";
import { useRef, useState, type ChangeEvent } from "react";
import { uploadImage, validateImageFile } from "../../api/files";

interface ImageUploadFieldProps {
  label: string;
  /** 이미 저장된 이미지 URL(수정 화면에서 미리보기용). 새로 첨부하기 전까지 이 값을 보여준다. */
  initialImageUrl?: string | null;
  /** 업로드가 끝나 objectKey가 생기면 호출된다. 첨부를 취소/실패하면 null로 호출된다. */
  onObjectKeyChange: (objectKey: string | null) => void;
  /**
   * 업로드 진행 상태가 바뀔 때마다 호출된다. 호출부는 이 값을 들고 있다가 업로드 중에는
   * 제출/저장 버튼을 막아야 한다 - 안 그러면 objectKey가 아직 null인 채로(또는 이전 값 그대로)
   * 저장 요청이 나가서, 방금 고른 이미지가 저장에서 빠지고 업로드된 파일만 고아로 남는다.
   */
  onUploadingChange?: (uploading: boolean) => void;
  /**
   * true면 기존 이미지가 있을 때 "삭제" 버튼을 보여준다(수정 화면 전용 - 새로 만드는 화면은
   * 지울 기존 이미지가 없으니 기본값 false). "새로 첨부 안 함"과 "명시적으로 지움"은 저장
   * 요청에서 의미가 다르므로(전자는 기존 값 유지, 후자는 null로 지움) onRemove로 별도 알린다.
   */
  removable?: boolean;
  /** 삭제 버튼을 누르면 호출된다. 미리보기도 함께 비운다. */
  onRemove?: () => void;
  disabled?: boolean;
}

/**
 * S3 presigned 업로드 흐름(선택 → 업로드 → objectKey 확보)을 감싼 이미지 첨부 필드.
 * objectKey는 아직 tmp 상태라 폼을 실제로 저장(도메인 API 호출)해야 uploads로 확정된다 -
 * 이 컴포넌트는 파일 선택~objectKey 확보까지만 책임진다.
 */
export function ImageUploadField({ label, initialImageUrl, onObjectKeyChange, onUploadingChange, removable = false, onRemove, disabled }: ImageUploadFieldProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(initialImageUrl ?? null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleRemove() {
    setPreviewUrl(null);
    setError(null);
    onObjectKeyChange(null);
    onRemove?.();
  }

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    const validationError = validateImageFile(file);
    if (validationError) {
      setError(validationError);
      return;
    }

    setPreviewUrl(URL.createObjectURL(file));
    setError(null);
    setUploading(true);
    onUploadingChange?.(true);
    onObjectKeyChange(null);
    try {
      const objectKey = await uploadImage(file);
      onObjectKeyChange(objectKey);
    } catch {
      setError("이미지 업로드에 실패했어요. 잠시 후 다시 시도해 주세요.");
      setPreviewUrl(initialImageUrl ?? null);
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
          className="relative grid size-24 shrink-0 place-items-center overflow-hidden rounded-button border border-dashed border-line bg-page text-muted hover:bg-card disabled:cursor-not-allowed disabled:opacity-60"
        >
          {previewUrl ? (
            <img src={previewUrl} alt="" className="size-full object-cover" />
          ) : (
            <ImagePlus size={22} />
          )}
          {uploading && (
            <span className="absolute inset-0 grid place-items-center bg-black/40 text-white">
              <Loader2 size={18} className="animate-spin" />
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
            {previewUrl ? "이미지 변경" : "이미지 선택"}
          </button>
          {removable && previewUrl && (
            <button
              type="button"
              disabled={disabled || uploading}
              onClick={handleRemove}
              className="inline-flex items-center gap-1 text-left text-sm font-bold text-primary-strong hover:underline disabled:cursor-not-allowed disabled:opacity-60"
            >
              <X size={14} />삭제
            </button>
          )}
          <span className="text-xs text-muted">jpg, png, webp / 최대 10MB</span>
          {error && <span className="text-xs font-bold text-primary-strong">{error}</span>}
        </div>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        className="hidden"
        onChange={handleFileChange}
      />
    </div>
  );
}
