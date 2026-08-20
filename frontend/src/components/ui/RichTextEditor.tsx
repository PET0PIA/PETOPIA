import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { EditorContent, useEditor, type Editor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Image from "@tiptap/extension-image";
import {
  Bold, Heading2, Heading3, ImagePlus, Italic, Link2, Link2Off, List, ListOrdered,
  Loader2, Quote, Redo2, Underline, Undo2,
} from "lucide-react";
import { validateImageFile } from "../../api/files";

interface RichTextEditorProps {
  /** 저장돼 있는 본문 HTML. 비어 있으면 빈 문자열. */
  value: string;
  /**
   * 내용이 바뀔 때마다 호출된다. 비어 있으면 에디터가 만드는 "&lt;p&gt;&lt;/p&gt;" 대신
   * 빈 문자열을 넘겨서, 호출부가 "본문 필수" 검사를 단순하게 할 수 있게 한다.
   */
  onChange: (html: string) => void;
  /**
   * 이미지 버튼을 쓰려면 넘겨야 한다. 파일을 받아 <b>바로 화면에 쓸 수 있는 URL</b>을 돌려준다.
   * (공지에서는 임시 업로드 → 서버 확정 → 공개 URL 순서를 이 함수가 담당한다.)
   * 넘기지 않으면 이미지 버튼이 비활성화된다.
   */
  onUploadImage?: (file: File) => Promise<string>;
  /** 업로드 중에는 호출부가 저장 버튼을 막아야 한다(ImageUploadField와 같은 규약). */
  onUploadingChange?: (uploading: boolean) => void;
  placeholder?: string;
  disabled?: boolean;
}

function ToolbarButton({ onClick, active, disabled, label, children }: {
  onClick: () => void;
  active?: boolean;
  disabled?: boolean;
  label: string;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      aria-pressed={active}
      title={label}
      className={`grid size-8 place-items-center rounded-button transition disabled:cursor-not-allowed disabled:opacity-40 ${
        active ? "bg-ink text-white" : "text-muted hover:bg-surface-alt hover:text-ink"
      }`}
    >
      {children}
    </button>
  );
}

/**
 * 공지 본문용 서식 편집기(Tiptap). 본문을 HTML로 만들고, 이미지도 글 중간에 넣을 수 있다.
 *
 * <p>여기서 만든 HTML은 <b>그대로 화면에 뿌리면 안 된다</b>. 읽는 쪽은 반드시
 * {@link ../common/RichTextView RichTextView}처럼 정화(sanitize)를 거쳐야 한다.
 */
export function RichTextEditor({
  value, onChange, onUploadImage, onUploadingChange, placeholder, disabled = false,
}: RichTextEditorProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [linkOpen, setLinkOpen] = useState(false);
  const [linkUrl, setLinkUrl] = useState("");
  const [error, setError] = useState<string | null>(null);

  const editor = useEditor({
    editable: !disabled,
    extensions: [
      StarterKit.configure({
        // 링크는 클릭해도 편집 중에는 열리지 않게 한다(글 쓰다가 페이지가 튀는 것 방지).
        link: { openOnClick: false, autolink: true },
      }),
      // base64 삽입을 막아 본문에 거대한 문자열이 박히는 걸 방지한다. 이미지는 항상 업로드 URL로만 들어온다.
      Image.configure({ allowBase64: false }),
    ],
    content: value || "",
    onUpdate: ({ editor: instance }) => onChange(instance.isEmpty ? "" : instance.getHTML()),
  });

  // 수정 화면을 열 때처럼 바깥에서 값이 통째로 바뀌는 경우에만 다시 심는다.
  // 입력 중(포커스 상태)에 심으면 커서가 맨 앞으로 튄다.
  useEffect(() => {
    if (!editor) return;
    const current = editor.isEmpty ? "" : editor.getHTML();
    if (value !== current && !editor.isFocused) {
      editor.commands.setContent(value || "", { emitUpdate: false });
    }
  }, [value, editor]);

  useEffect(() => {
    editor?.setEditable(!disabled);
  }, [disabled, editor]);

  if (!editor) return null;

  async function handleImageChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !onUploadImage) return;

    const validationError = validateImageFile(file);
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    setUploading(true);
    onUploadingChange?.(true);
    try {
      const url = await onUploadImage(file);
      (editor as Editor).chain().focus().setImage({ src: url, alt: file.name }).run();
    } catch {
      setError("이미지 업로드에 실패했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setUploading(false);
      onUploadingChange?.(false);
    }
  }

  function openLinkInput() {
    setLinkUrl(editor?.getAttributes("link").href ?? "");
    setLinkOpen(true);
  }

  function applyLink() {
    const url = linkUrl.trim();
    if (url === "") {
      editor?.chain().focus().unsetLink().run();
    } else {
      editor?.chain().focus().extendMarkRange("link").setLink({ href: url }).run();
    }
    setLinkOpen(false);
  }

  return (
    <div>
      <div className={`overflow-hidden rounded-card ring-1 ring-line ${disabled ? "opacity-60" : ""}`}>
        <div className="flex flex-wrap items-center gap-0.5 border-b border-line bg-surface-alt px-2 py-1.5">
          <ToolbarButton label="굵게" onClick={() => editor.chain().focus().toggleBold().run()} active={editor.isActive("bold")} disabled={disabled}>
            <Bold size={16} />
          </ToolbarButton>
          <ToolbarButton label="기울임" onClick={() => editor.chain().focus().toggleItalic().run()} active={editor.isActive("italic")} disabled={disabled}>
            <Italic size={16} />
          </ToolbarButton>
          <ToolbarButton label="밑줄" onClick={() => editor.chain().focus().toggleUnderline().run()} active={editor.isActive("underline")} disabled={disabled}>
            <Underline size={16} />
          </ToolbarButton>
          <span className="mx-1 h-5 w-px bg-line" />
          <ToolbarButton label="제목" onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()} active={editor.isActive("heading", { level: 2 })} disabled={disabled}>
            <Heading2 size={16} />
          </ToolbarButton>
          <ToolbarButton label="작은 제목" onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()} active={editor.isActive("heading", { level: 3 })} disabled={disabled}>
            <Heading3 size={16} />
          </ToolbarButton>
          <ToolbarButton label="목록" onClick={() => editor.chain().focus().toggleBulletList().run()} active={editor.isActive("bulletList")} disabled={disabled}>
            <List size={16} />
          </ToolbarButton>
          <ToolbarButton label="번호 목록" onClick={() => editor.chain().focus().toggleOrderedList().run()} active={editor.isActive("orderedList")} disabled={disabled}>
            <ListOrdered size={16} />
          </ToolbarButton>
          <ToolbarButton label="인용" onClick={() => editor.chain().focus().toggleBlockquote().run()} active={editor.isActive("blockquote")} disabled={disabled}>
            <Quote size={16} />
          </ToolbarButton>
          <span className="mx-1 h-5 w-px bg-line" />
          <ToolbarButton label={editor.isActive("link") ? "링크 수정" : "링크"} onClick={openLinkInput} active={editor.isActive("link")} disabled={disabled}>
            <Link2 size={16} />
          </ToolbarButton>
          <ToolbarButton label="링크 해제" onClick={() => editor.chain().focus().unsetLink().run()} disabled={disabled || !editor.isActive("link")}>
            <Link2Off size={16} />
          </ToolbarButton>
          <ToolbarButton
            label={onUploadImage ? "사진 넣기" : "사진 넣기(사용 불가)"}
            onClick={() => fileInputRef.current?.click()}
            disabled={disabled || uploading || !onUploadImage}
          >
            {uploading ? <Loader2 size={16} className="animate-spin" /> : <ImagePlus size={16} />}
          </ToolbarButton>
          <span className="mx-1 h-5 w-px bg-line" />
          <ToolbarButton label="실행 취소" onClick={() => editor.chain().focus().undo().run()} disabled={disabled || !editor.can().undo()}>
            <Undo2 size={16} />
          </ToolbarButton>
          <ToolbarButton label="다시 실행" onClick={() => editor.chain().focus().redo().run()} disabled={disabled || !editor.can().redo()}>
            <Redo2 size={16} />
          </ToolbarButton>
        </div>

        {linkOpen && (
          <div className="flex items-center gap-2 border-b border-line bg-page px-3 py-2">
            <input
              type="url"
              value={linkUrl}
              onChange={(event) => setLinkUrl(event.target.value)}
              onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); applyLink(); } }}
              placeholder="https://example.com (비우고 적용하면 링크 해제)"
              className="min-w-0 flex-1 rounded-button border border-line px-2.5 py-1.5 text-sm outline-none focus:border-ink"
              autoFocus
            />
            <button type="button" onClick={applyLink} className="shrink-0 rounded-button bg-ink px-3 py-1.5 text-sm font-bold text-white">적용</button>
            <button type="button" onClick={() => setLinkOpen(false)} className="shrink-0 rounded-button px-2 py-1.5 text-sm text-muted hover:text-ink">취소</button>
          </div>
        )}

        <div className="relative bg-page">
          <EditorContent editor={editor} className="rich-text min-h-56 px-4 py-3 text-sm [&_.tiptap]:min-h-52 [&_.tiptap]:outline-none" />
          {placeholder && editor.isEmpty && (
            <p className="pointer-events-none absolute left-4 top-3 text-sm text-muted">{placeholder}</p>
          )}
        </div>
      </div>

      <input ref={fileInputRef} type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={handleImageChange} />
      {error && <p className="mt-1.5 text-xs font-bold text-primary-strong">{error}</p>}
    </div>
  );
}
