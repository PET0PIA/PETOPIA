package com.ms.petopia.api.chat.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 상태 판정 테스트.
 *
 * 값이 다섯 개뿐인 enum에 테스트를 붙이는 이유는, 이 판정이 두 곳에 복제돼 있기 때문이다.
 * 여기와 ChatConversationMapper.xml의 조건부 UPDATE. 실제 차단은 SQL이 하고 이쪽은 화면
 * 표시용이라, 한쪽만 고치면 "입력창은 열려 있는데 전송은 거부되는" 화면이 만들어진다.
 * ChatConversationMapperSqlContractTest가 두 목록이 같은지 검사하고, 이 테스트는 그 기준이
 * 되는 규칙 자체를 못 박는다.
 */
class ChatConversationStatusTest {

    @ParameterizedTest
    @EnumSource(value = ChatConversationStatus.class, names = "CLOSED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("종료가 아니면 언제나 메시지를 받는다 - 답변을 기다린다는 이유만으로 막지 않는다")
    void acceptsUserMessage_종료가_아니면_참이다(ChatConversationStatus status) {
        assertThat(status.acceptsUserMessage()).isTrue();
    }

    @Test
    @DisplayName("종료된 대화만 잠긴다")
    void acceptsUserMessage_종료만_거짓이다() {
        assertThat(ChatConversationStatus.CLOSED.acceptsUserMessage()).isFalse();
        assertThat(ChatLockReason.of(ChatConversationStatus.CLOSED)).isEqualTo(ChatLockReason.CLOSED);
    }

    @Test
    @DisplayName("AI가 응대한 대화도 입력이 열려 있고 잠금 사유가 없다")
    void AI_HANDLED는_잠기지_않는다() {
        assertThat(ChatConversationStatus.AI_HANDLED.acceptsUserMessage()).isTrue();
        assertThat(ChatLockReason.of(ChatConversationStatus.AI_HANDLED)).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = ChatConversationStatus.class, names = "WAITING_AGENT", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("상담사 답변이 필요한 상태는 WAITING_AGENT 하나뿐이다 - AI 응대 건이 대기열 건수에 섞이면 실제 업무량과 어긋난다")
    void needsAgentReply_대기중만_참이다(ChatConversationStatus status) {
        assertThat(status.needsAgentReply()).isFalse();
    }

    @Test
    @DisplayName("대기 중은 상담사 답변이 필요하다")
    void needsAgentReply_대기중은_참이다() {
        assertThat(ChatConversationStatus.WAITING_AGENT.needsAgentReply()).isTrue();
    }

    @Test
    @DisplayName("AI는 답변 유형이 아니다 - 상수가 없으면 API가 그 값을 받아들일 경로도 없다")
    void 답변유형에_AI는_없다() {
        assertThat(ChatAnswerType.values())
                .containsExactly(ChatAnswerType.FIXED, ChatAnswerType.AGENT);
    }
}
