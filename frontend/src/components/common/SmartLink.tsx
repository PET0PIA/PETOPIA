import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import type { LinkTarget } from "../../api/banner";

// 앱 내부 경로: "/"로 시작하되 "//"(프로토콜 상대 URL, 예: //evil.com)는 제외.
const INTERNAL_PATH_PATTERN = /^\/(?!\/)/;
// 지원하는 외부 URL: http/https 뿐. 그 외(프로토콜 상대, javascript: 등 미지원 스킴)는 링크를 만들지 않는다.
const EXTERNAL_URL_PATTERN = /^https?:\/\//;

interface SmartLinkProps {
  to: string;
  target: LinkTarget | null;
  className?: string;
  children: ReactNode;
}

/**
 * 관리자가 입력한 linkUrl(내부 경로 또는 외부 URL)을 안전하게 링크로 렌더링한다.
 * 내부 경로면 SPA 라우팅(<Link>), http/https 외부 URL이면 일반 <a>, 그 외(프로토콜 상대 등
 * 미지원 형식)는 렌더링하지 않는다. 배너 히어로 섹션과 팝업 모달이 공유해서 쓴다.
 */
export function SmartLink({ to, target, className, children }: SmartLinkProps) {
  const openInNewTab = target === "BLANK";
  if (INTERNAL_PATH_PATTERN.test(to)) {
    return (
      <Link to={to} target={openInNewTab ? "_blank" : undefined} className={className}>
        {children}
      </Link>
    );
  }
  if (EXTERNAL_URL_PATTERN.test(to)) {
    return (
      <a href={to} target={openInNewTab ? "_blank" : undefined} rel={openInNewTab ? "noopener noreferrer" : undefined} className={className}>
        {children}
      </a>
    );
  }
  return null;
}
