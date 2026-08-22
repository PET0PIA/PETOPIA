import { useState, type FormEvent, type KeyboardEvent } from "react";
import { Send } from "lucide-react";
import type { ChatLockReason } from "../../api/chat";

interface ChatComposerProps {
  locked: boolean;
  lockReason: ChatLockReason | null;
  sending: boolean;
  onSend: (content: string) => void;
}

/**
 * 잠금 사유별 안내.
 *
 * 사유가 하나뿐이어도 Record로 남긴다. 사유가 늘어날 때 분기를 다시 만드는 것보다
 * 이 표에 한 줄을 더하는 편이 낫고, 타입이 빠짐없이 채우도록 강제한다.
 */
const LOCK_NOTICE: Record<ChatLockReason, string> = {
  CLOSED: "종료된 상담이에요. 새로 문의하시려면 아래에서 다시 시작해주세요.",
};

export function ChatComposer({ locked, lockReason, sending, onSend }: ChatComposerProps) {
  const [value, setValue] = useState("");

  const submit = () => {
    const trimmed = value.trim();
    if (!trimmed || locked || sending) return;
    onSend(trimmed);
    setValue("");
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    submit();
  };

  /*
   * Enter로 보내고 Shift+Enter로 줄바꿈한다. 문의 내용은 여러 줄이 되는 경우가 많아
   * textarea를 쓰되, 메신저의 관행대로 Enter를 전송에 둔다.
   *
   * isComposing 검사가 핵심이다. 한글은 자모를 조합하는 중에도 keydown이 발생하는데,
   * 그때 전송해버리면 "안녕하세요"의 마지막 글자가 아직 조합 중이라 전송 뒤 입력창으로
   * 되돌아온다(입력창에 '요'만 남는 증상). 조합이 끝난 뒤의 Enter만 전송으로 본다.
   */
  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  };

  if (locked && lockReason) {
    return (
      <div className="border-t border-line px-4 py-3">
        <p className="text-center text-xs leading-relaxed text-muted">{LOCK_NOTICE[lockReason]}</p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex items-end gap-2 border-t border-line px-4 py-3">
      <label htmlFor="chat-composer" className="sr-only">
        문의 내용
      </label>
      <textarea
        id="chat-composer"
        rows={1}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        onKeyDown={handleKeyDown}
        maxLength={2000}
        placeholder="문의 내용을 입력해주세요"
        className="max-h-24 min-h-11 flex-1 resize-none rounded-button border border-line bg-card px-3 py-2 text-sm text-ink placeholder:text-muted"
      />
      <button
        type="submit"
        disabled={sending || value.trim().length === 0}
        aria-label="메시지 보내기"
        className="flex min-h-11 min-w-11 items-center justify-center rounded-button bg-primary-strong text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
      >
        <Send size={18} aria-hidden />
      </button>
    </form>
  );
}
