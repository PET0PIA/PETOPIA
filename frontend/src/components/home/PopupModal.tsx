import { useEffect, useId, useRef, useState, type CSSProperties } from "react";
import { X } from "lucide-react";
import { getActivePopups, type Popup } from "../../api/popup";
import { SmartLink } from "../common/SmartLink";
import { isSmartLinkable } from "../../utils/linkClassification";

const focusableSelector = [
  "a[href]",
  "button:not([disabled])",
  "textarea:not([disabled])",
  "input:not([disabled])",
  "select:not([disabled])",
  "[tabindex]:not([tabindex='-1'])",
].join(",");

function todayDateString(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

function hiddenTodayKey(popupId: number): string {
  return `popup_${popupId}_hidden_${todayDateString()}`;
}

/**
 * 메인페이지 진입 시 활성 팝업을 순차 표시하는 모달. Dialog(components/ui)와 달리 제목
 * 표시줄이 없는 광고형 레이아웃이라 별도로 만들되, 포커스 트랩/ESC 닫기 같은 접근성
 * 처리는 Dialog와 동일한 패턴을 따른다.
 */
export function PopupModal() {
  // null = 아직 안 불러옴. 배열은 "오늘 하루 보지 않기"로 숨겨진 것을 뺀 표시 대기열이고,
  // 하나 닫을 때마다 앞에서 하나씩 꺼내(순차 표시) 다음 팝업이 이어서 뜬다.
  const [queue, setQueue] = useState<Popup[] | null>(null);
  const dialogRef = useRef<HTMLElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const titleId = useId();

  useEffect(() => {
    let ignore = false;
    getActivePopups()
      .then((popups) => {
        if (ignore) return;
        setQueue(popups.filter((popup) => localStorage.getItem(hiddenTodayKey(popup.popupId)) === null));
      })
      .catch(() => { if (!ignore) setQueue([]); });
    return () => { ignore = true; };
  }, []);

  const popup = queue && queue.length > 0 ? queue[0] : null;

  function dismiss() {
    setQueue((previous) => (previous && previous.length > 0 ? previous.slice(1) : previous));
  }

  function hideToday() {
    if (popup) localStorage.setItem(hiddenTodayKey(popup.popupId), "1");
    dismiss();
  }

  useEffect(() => {
    if (!popup) return;

    previousFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const focusFrame = window.requestAnimationFrame(() => {
      const focusableElements = dialogRef.current?.querySelectorAll<HTMLElement>(focusableSelector);
      (focusableElements?.[0] ?? dialogRef.current)?.focus();
    });

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        dismiss();
        return;
      }
      if (event.key !== "Tab") return;

      const focusableElements = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(focusableSelector) ?? []);
      if (focusableElements.length === 0) {
        event.preventDefault();
        dialogRef.current?.focus();
        return;
      }
      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];
      if (event.shiftKey && document.activeElement === firstElement) {
        event.preventDefault();
        lastElement.focus();
      } else if (!event.shiftKey && document.activeElement === lastElement) {
        event.preventDefault();
        firstElement.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      window.cancelAnimationFrame(focusFrame);
      document.removeEventListener("keydown", handleKeyDown);
      previousFocusRef.current?.focus();
    };
    // popup.popupId가 바뀔 때(다음 팝업으로 넘어갈 때)마다 포커스를 다시 잡아야 한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [popup?.popupId]);

  if (!popup) return null;

  const sizeStyle: CSSProperties = {
    width: popup.width ? `min(${popup.width}px, 90vw)` : undefined,
    maxHeight: popup.height ? `min(${popup.height}px, 90vh)` : "90vh",
  };

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-ink/30 p-4"
      role="presentation"
      onMouseDown={(event) => { if (event.target === event.currentTarget) dismiss(); }}
    >
      <section
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="surface relative flex w-full max-w-sm flex-col overflow-hidden"
        style={sizeStyle}
      >
        {/* 팝업 광고엔 화면에 보이는 제목 표시줄이 없어서 스크린리더용으로만 둔다. */}
        <h2 id={titleId} className="sr-only">{popup.title}</h2>
        <button
          type="button"
          aria-label="팝업 닫기"
          onClick={dismiss}
          className="absolute right-2 top-2 z-10 rounded-full bg-ink/50 p-1.5 text-white hover:bg-ink/70"
        >
          <X size={18} />
        </button>

        <div className="flex-1 overflow-y-auto" style={{ backgroundColor: popup.bgColor ?? undefined }}>
          {popup.imageKey ? (
            <>
              {popup.linkUrl && isSmartLinkable(popup.linkUrl) ? (
                <SmartLink to={popup.linkUrl} target={popup.linkTarget} className="block">
                  {/* 이미지 자체가 링크라 스크린리더에 링크 목적을 알려줄 이름이 필요하다. */}
                  <img src={popup.imageKey} alt={popup.title} className="w-full" />
                </SmartLink>
              ) : (
                <img src={popup.imageKey} alt="" className="w-full" />
              )}
              {popup.subtitle && <p className="whitespace-pre-line p-4 text-sm leading-6 text-ink">{popup.subtitle}</p>}
            </>
          ) : (
            popup.subtitle && <p className="whitespace-pre-line p-6 text-base leading-7 text-ink">{popup.subtitle}</p>
          )}
          {popup.linkLabel && popup.linkUrl && (
            <div className="px-4 pb-4">
              <SmartLink
                to={popup.linkUrl}
                target={popup.linkTarget}
                className="inline-flex min-h-11 w-full items-center justify-center rounded-button bg-primary px-5 text-sm font-bold text-white transition hover:opacity-90"
              >
                {popup.linkLabel}
              </SmartLink>
            </div>
          )}
        </div>

        <div className="flex items-center justify-between border-t border-line px-4 py-3 text-sm">
          <button type="button" onClick={hideToday} className="font-bold text-muted hover:text-ink">
            오늘 하루 보지 않기
          </button>
          <button type="button" onClick={dismiss} className="font-bold text-ink hover:underline">
            닫기
          </button>
        </div>
      </section>
    </div>
  );
}
