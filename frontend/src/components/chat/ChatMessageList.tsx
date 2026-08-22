import { useEffect, useRef } from "react";
import type { ChatMessage } from "../../api/chat";
import { ChatBubble } from "./ChatBubble";

interface ChatMessageListProps {
  greeting: string;
  messages: ChatMessage[];
}

/**
 * SYSTEM 메시지는 말풍선이 아니라 가운데 정렬된 안내문으로 그린다.
 * 접수 안내·운영시간 안내는 대화 상대가 한 말이 아니라 상태 알림이라, 말풍선으로 두면
 * 사용자가 "누가 답한 것"으로 읽는다.
 */
function SystemNote({ content }: { content: string }) {
  return (
    <p className="whitespace-pre-line px-6 text-center text-xs leading-relaxed text-muted">
      {content}
    </p>
  );
}

/**
 * 상담이 바뀌는 지점에 넣는 구분선.
 *
 * 이게 없으면 지난 상담의 답변이 방금 시작한 문의의 답처럼 읽힌다 - 특히 며칠 전 답변이
 * 바로 위에 붙어 있으면 사용자는 그게 지금 받은 답이라고 오해한다.
 */
function ConversationDivider() {
  return (
    <div className="flex items-center gap-2 py-1">
      <span className="h-px flex-1 bg-line" />
      <span className="text-[11px] text-muted">여기서부터 새 문의</span>
      <span className="h-px flex-1 bg-line" />
    </div>
  );
}

export function ChatMessageList({ greeting, messages }: ChatMessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  // 새 메시지가 오면 바닥으로 붙인다. 상담사 답변이 도착했는데 화면이 위에 머물러 있으면
  // 사용자는 답변이 오지 않았다고 판단한다.
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages.length]);

  return (
    <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
      {greeting && <SystemNote content={greeting} />}
      {messages.map((message, index) => {
        // 앞 메시지와 상담이 다르면(맨 처음 제외) 그 사이에 구분선을 넣는다.
        const previous = messages[index - 1];
        const startsNewConversation =
          previous != null && previous.conversationId !== message.conversationId;

        return (
          <div key={message.messageId} className="space-y-3">
            {startsNewConversation && <ConversationDivider />}
            {message.senderType === "SYSTEM" ? (
              <SystemNote content={message.content} />
            ) : (
              <ChatBubble senderType={message.senderType} content={message.content} />
            )}
          </div>
        );
      })}
      <div ref={bottomRef} />
    </div>
  );
}
