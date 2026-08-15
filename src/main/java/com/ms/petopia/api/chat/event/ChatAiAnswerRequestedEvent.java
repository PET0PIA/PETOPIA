package com.ms.petopia.api.chat.event;

/**
 * AI가 답변해야 하는 문의가 접수됐다.
 *
 * <p>이벤트로 넘기는 이유는 두 가지다.
 * <ul>
 *   <li><b>커밋 이후에</b> 처리하려고. 사용자 메시지가 아직 커밋되지 않은 상태에서 AI가 먼저
 *       답하면, 답변이 질문보다 앞서 보이거나 롤백된 질문에 답이 달린다.</li>
 *   <li>사용자 요청을 붙잡지 않으려고. Claude 호출은 수 초가 걸리는데, 그동안 전송 요청이
 *       응답을 못 하면 사용자는 메시지가 안 보내진 줄 알고 다시 누른다.</li>
 * </ul>
 *
 * @param aiContext      이 문의 유형에 설정된 도메인 지식(운영자가 관리자 화면에서 편집한다)
 * @param lastAnswer     이번이 마지막 허용 횟수인지. 마지막이면 답변 뒤에 종료 안내를 붙이고
 *                       입력을 잠근다. 호출 시점에 이미 슬롯을 선점했으므로 여기서 다시
 *                       세지 않고 그때 계산한 값을 실어 보낸다.
 */
public record ChatAiAnswerRequestedEvent(
        Long conversationId,
        String aiContext,
        boolean lastAnswer
) {
}
