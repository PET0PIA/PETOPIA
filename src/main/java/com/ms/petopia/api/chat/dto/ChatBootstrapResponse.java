package com.ms.petopia.api.chat.dto;

import java.util.List;

/**
 * 위젯을 열었을 때 한 번에 필요한 것들. 인사말·버튼·운영시간·지난 대화를 한 요청으로 받는다.
 *
 * @param history 이 사용자의 최근 메시지들(여러 상담에 걸쳐, 오래된 순).
 *                채팅창은 상담 단위로 끊기지 않고 하나로 이어져야 한다 - 새 문의를 시작했다고
 *                앞서 받은 답변이 화면에서 사라지면, 사용자는 방금 읽은 내용을 다시 볼 수 없다.
 * @param ongoing 가장 최근 대화의 <b>상태</b>(잠금 여부·전송 대상 ID). 메시지 본문은 history가
 *                이미 담고 있으므로 화면은 이 필드를 상태 판정에만 쓴다.
 */
public record ChatBootstrapResponse(
        String greeting,
        List<ChatMenuResponse> menus,
        boolean withinBusinessHours,
        List<ChatMessageResponse> history,
        ChatConversationResponse ongoing
) {
}
