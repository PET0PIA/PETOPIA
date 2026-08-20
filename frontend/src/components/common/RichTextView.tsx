import DOMPurify from "dompurify";
import { useMemo } from "react";

interface RichTextViewProps {
  /** 에디터가 만든 본문 HTML. */
  html: string;
  className?: string;
}

/**
 * 관리자가 에디터로 쓴 HTML을 화면에 뿌리는 유일한 통로.
 *
 * <p>서버에 저장된 본문은 HTML이라 그대로 dangerouslySetInnerHTML에 넣으면 스크립트가
 * 함께 실행될 수 있다. 여기서 허용 목록에 없는 태그·속성을 모두 걷어낸 뒤에만 그린다.
 * <b>본문 HTML을 그릴 일이 생기면 이 컴포넌트를 쓰고, 직접 innerHTML을 쓰지 않는다.</b>
 */
const ALLOWED_TAGS = [
  "p", "br", "strong", "em", "u", "s", "h2", "h3", "ul", "ol", "li",
  "a", "img", "blockquote", "code", "pre", "hr",
];
const ALLOWED_ATTR = ["href", "target", "rel", "src", "alt", "title"];

export function RichTextView({ html, className = "" }: RichTextViewProps) {
  const safeHtml = useMemo(
    () => DOMPurify.sanitize(html ?? "", { ALLOWED_TAGS, ALLOWED_ATTR }),
    [html],
  );
  return <div className={`rich-text ${className}`} dangerouslySetInnerHTML={{ __html: safeHtml }} />;
}
