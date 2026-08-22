package com.ms.petopia.api.chat.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * 위젯을 열었을 때 한 번에 필요한 것들. 인사말·버튼·운영시간·지난 대화를 한 요청으로 받는다.
 *
 * @param history 이 사용자의 최근 메시지들(여러 상담에 걸쳐, 오래된 순).
 *                채팅창은 상담 단위로 끊기지 않고 하나로 이어져야 한다 - 새 문의를 시작했다고
 *                앞서 받은 답변이 화면에서 사라지면, 사용자는 방금 읽은 내용을 다시 볼 수 없다.
 * @param closesAt 오늘 상담이 끝나는 시각. <b>운영시간 안일 때만 채운다</b> - 밖에서는
 *                 이 값이 없어야 화면이 "닫혀 있는데 종료 시각을 보여주는" 상태가 되지 않는다.
 * @param hasHistory 지난 상담이 하나라도 있는지. {@code 문의 내역} 버튼을 띄울지 판단하는
 *                   값이다. 프론트가 {@code history.length}로 재현하지 않게 따로 내린다 -
 *                   나중에 이력에 페이지네이션이 붙어 첫 페이지가 비는 순간 판정이 어긋난다.
 * @param ongoing 가장 최근 대화의 <b>상태</b>(잠금 여부·전송 대상 ID). 메시지 본문은 history가
 *                이미 담고 있으므로 화면은 이 필드를 상태 판정에만 쓴다.
 */
public record ChatBootstrapResponse(
        String greeting,
        List<ChatMenuResponse> menus,
        boolean withinBusinessHours,
        LocalTime closesAt,
        List<ChatMessageResponse> history,
        boolean hasHistory,
        ChatConversationResponse ongoing
) {
}
