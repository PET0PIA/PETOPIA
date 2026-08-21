import { useEffect, useId, useState, type CSSProperties } from "react";
import { X } from "lucide-react";
import { getActivePopups, type Popup } from "../../api/popup";
import { SmartLink } from "../common/SmartLink";
import { isSmartLinkable } from "../../utils/linkClassification";

// 슬라이드다운 트랜지션(duration-300)이 끝날 시간을 준 뒤 큐에서 실제로 빼낸다.
const EXIT_ANIMATION_MS = 300;

function todayDateString(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

function hiddenTodayKey(popupId: number): string {
  return `popup_${popupId}_hidden_${todayDateString()}`;
}

/**
 * 메인페이지 진입 시 활성 팝업을 좌측 하단에 순차 표시하는 비모달 배너.
 * 뒷화면을 가리거나 조작을 막지 않으며, 등장 시 아래에서 위로 슬라이드업된다.
 */
export function PopupModal() {
  // null = 아직 안 불러옴. 배열은 "오늘 하루 보지 않기"로 숨겨진 것을 뺀 표시 대기열이고,
  // 하나 닫을 때마다 앞에서 하나씩 꺼내(순차 표시) 다음 팝업이 이어서 뜬다.
  const [queue, setQueue] = useState<Popup[] | null>(null);
  // 슬라이드업/다운 트랜지션 트리거용. 큐의 popup과 별개로 다뤄야 닫힐 때
  // 슬라이드다운 애니메이션이 끝날 때까지 카드가 화면에 남아있을 수 있다.
  const [visible, setVisible] = useState(false);
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

  useEffect(() => {
    if (!popup) return;
    const frame = window.requestAnimationFrame(() => setVisible(true));
    return () => window.cancelAnimationFrame(frame);
  }, [popup?.popupId]);

  function closeWithAnimation(after: () => void) {
    setVisible(false);
    window.setTimeout(after, EXIT_ANIMATION_MS);
  }

  function dismiss() {
    closeWithAnimation(() => {
      setQueue((previous) => (previous && previous.length > 0 ? previous.slice(1) : previous));
    });
  }

  function hideToday() {
    if (popup) localStorage.setItem(hiddenTodayKey(popup.popupId), "1");
    dismiss();
  }

  if (!popup) return null;

  const sizeStyle: CSSProperties = {
    width: popup.width ? `${popup.width}px` : undefined,
    maxHeight: popup.height ? `min(${popup.height}px, 70vh)` : "70vh",
  };

  return (
    <section
      role="region"
      aria-labelledby={titleId}
      className={`fixed bottom-6 left-6 z-40 flex w-full max-w-[320px] flex-col overflow-hidden rounded-card shadow-lg surface transition-all duration-300 ease-out ${
        visible ? "translate-y-0 opacity-100" : "translate-y-full opacity-0"
      }`}
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
  );
}
