import type { ChatSenderType } from "../../api/chat";

interface ChatBubbleProps {
  senderType: ChatSenderType;
  content: string;
}

/**
 * 말풍선 하나.
 *
 * {@link ChatMessageList}에서 떼어낸 이유는 고정 답변 화면이 같은 모양을 써야 하기
 * 때문이다. 그 화면은 <b>메시지가 아니다</b> - 서버에 행이 생기지 않으므로 messageId도
 * conversationId도 없다. 그래서 ChatMessage를 흉내 낸 객체를 만들어 넘기는 대신, 화면에
 * 실제로 필요한 두 값만 받는 표현 전용 컴포넌트로 분리했다. 가짜 ID를 만들면 그것이
 * 언젠가 transcript에 섞여 서버 커서를 망가뜨린다.
 */
export function ChatBubble({ senderType, content }: ChatBubbleProps) {
  const isUser = senderType === "USER";

  return (
    <div className={`flex ${isUser ? "justify-end" : "justify-start"}`}>
      <div className="max-w-[85%]">
        {/*
          AI 답변에는 배지를 붙인다. 사람이 쓴 답변과 구분되지 않으면, 자동 답변에 섞인
          오정보를 사용자가 상담사의 확답으로 받아들인다.

          고정 답변(BOT)에는 붙이지 않는다. 사용자가 방금 누른 버튼에 대한 저장된 안내라
          출처가 화면에 이미 드러나 있고, 매번 배지를 달면 정작 AI 답변의 배지가 묻힌다.
        */}
        {senderType === "AI" && (
          <span className="mb-1 inline-block rounded-pill bg-sun-soft px-2 py-0.5 text-[11px] font-bold text-ink">
            자동 답변
          </span>
        )}
        <div
          className={`whitespace-pre-line rounded-card px-3 py-2 text-sm leading-relaxed ${
            isUser ? "bg-primary-strong text-white" : "bg-surface-alt text-ink"
          }`}
        >
          {content}
        </div>
      </div>
    </div>
  );
}
