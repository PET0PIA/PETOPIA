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
 * <p>대화 ID 하나만 싣는다. 참고 지식({@code AI_CONTEXT})은 전역 설정이라 소비하는 쪽이
 * 직접 읽는 편이 낫고 - 이벤트에 실으면 발행 시점의 값이 얼어붙는다 - 남은 횟수 개념은
 * 한도가 사라지면서 함께 없어졌다.
 *
 */
public record ChatAiAnswerRequestedEvent(Long conversationId) {
}
