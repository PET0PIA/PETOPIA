package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.dto.ChatConversationStatus;
import com.ms.petopia.api.chat.dto.ChatSenderType;
import com.ms.petopia.api.chat.event.ChatAiAnswerRequestedEvent;
import com.ms.petopia.api.chat.mapper.ChatConversationMapper;
import com.ms.petopia.api.chat.mapper.ChatMessageMapper;
import com.ms.petopia.api.chat.mapper.ChatSettingMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/*
 * ChatAiAnswerService 단위 테스트. 답변 본문이 어떻게 저장되는지와, 답하지 못한 경우
 * 화면에 무엇이 남는지를 본다. Claude 호출 자체(ClaudeSupportResponder)는 Mock이다.
 */
@ExtendWith(MockitoExtension.class)
class ChatAiAnswerServiceTest {

    private static final long CONVERSATION_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 22, 0);
    private static final String CLOSING_NOTE = "더 상세한 답변이 필요하시면 운영시간에 다시 문의해주세요.";
    private static final String ESCALATE_NOTE = "상담사가 확인 후 답변드릴게요.";

    @Mock private ClaudeSupportResponder responder;
    @Mock private ChatMessageWriter messageWriter;
    @Mock private ChatConversationMapper conversationMapper;
    @Mock private ChatMessageMapper messageMapper;
    @Mock private ChatSettingMapper settingMapper;
    @Mock private ChatTimeProvider timeProvider;

    @InjectMocks private ChatAiAnswerService service;

    private final ChatAiAnswerRequestedEvent event = new ChatAiAnswerRequestedEvent(CONVERSATION_ID);

    private void givenAnswer(String answer) {
        given(timeProvider.now()).willReturn(NOW);
        given(messageMapper.selectRecentByConversation(eq(CONVERSATION_ID), anyInt()))
                .willReturn(List.of());
        given(settingMapper.selectValue("AI_CONTEXT")).willReturn("참고 정보");
        // 전이 실패 경로는 본문을 만들기 전에 끝나므로 이 값을 읽지 않는다. 공통 준비에
        // 남겨두되 그 한 건만 미사용을 허용한다 - 나머지 스텁은 STRICT_STUBS로 계속 검출한다.
        lenient().when(settingMapper.selectValue("AI_CLOSING_NOTE")).thenReturn(CLOSING_NOTE);
        given(responder.answer(eq(CONVERSATION_ID), any(), eq("참고 정보")))
                .willReturn(Optional.of(answer));
    }

    /**
     * 응답기가 답하지 않기로 한 경우.
     *
     * <p>AI_CONTEXT까지 준비한다. 이관 판정도 프롬프트 조립을 거치므로 서비스가 그 값을
     * 읽는데, 준비하지 않으면 STRICT_STUBS가 "다른 인자로 호출됐다"로 먼저 실패한다.
     */
    private void givenEscalation() {
        given(messageMapper.selectRecentByConversation(eq(CONVERSATION_ID), anyInt()))
                .willReturn(List.of());
        given(settingMapper.selectValue("AI_CONTEXT")).willReturn("참고 정보");
        given(settingMapper.selectValue("AI_ESCALATE_NOTICE")).willReturn(ESCALATE_NOTE);
        given(responder.answer(eq(CONVERSATION_ID), any(), eq("참고 정보")))
                .willReturn(Optional.empty());
    }

    @Test
    @DisplayName("답변 본문 뒤에 마무리 안내가 같은 말풍선으로 붙는다 - 따로 붙이면 창의 절반이 안내로 찬다")
    void tryAnswer_마무리안내가_한_말풍선에_붙는다() {
        givenAnswer("예약 확인은 마이페이지에서 하실 수 있어요.");
        given(conversationMapper.markAiHandled(CONVERSATION_ID, NOW)).willReturn(1);

        boolean answered = service.tryAnswer(event);

        assertThat(answered).isTrue();
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(messageWriter).append(eq(CONVERSATION_ID), eq(ChatSenderType.AI), isNull(), isNull(),
                body.capture());
        assertThat(body.getValue())
                .startsWith("예약 확인은 마이페이지에서 하실 수 있어요.")
                .endsWith(CLOSING_NOTE);
    }

    @Test
    @DisplayName("성공하면 AI_HANDLED로 넘기고 지표 카운터를 올린다")
    void tryAnswer_성공하면_AI_HANDLED로_넘긴다() {
        givenAnswer("답변입니다.");
        given(conversationMapper.markAiHandled(CONVERSATION_ID, NOW)).willReturn(1);

        service.tryAnswer(event);

        verify(conversationMapper).incrementAiAnswerCount(CONVERSATION_ID);
        verify(messageWriter).publishStatus(CONVERSATION_ID, ChatConversationStatus.AI_HANDLED);
    }

    @Test
    @DisplayName("전이가 실패하면 답변도 지표도 남기지 않는다 - 사람이 이어받은 대화에 자동 답변이 끼어들면 안 된다")
    void tryAnswer_전이실패하면_아무것도_저장하지_않는다() {
        givenAnswer("답변입니다.");
        // 그 사이 상담사가 답했거나(IN_PROGRESS) 사용자가 종료했다(CLOSED).
        given(conversationMapper.markAiHandled(CONVERSATION_ID, NOW)).willReturn(0);

        boolean answered = service.tryAnswer(event);

        assertThat(answered).isFalse();
        // 순서가 반대였을 때 실제로 남던 세 가지다. 상태만 지키고 말풍선을 붙이면 화면에는
        // 막으려던 그 답변이 그대로 나타난다.
        verify(messageWriter, never()).append(anyLong(), any(), any(), any(), any());
        verify(conversationMapper, never()).incrementAiAnswerCount(anyLong());
        verify(messageWriter, never()).publishStatus(anyLong(), any());
    }

    @Test
    @DisplayName("답하지 못하면 이관 안내를 남긴다 - 조용히 끝내면 '입력창은 열려 있는데 답이 없는' 화면이 된다")
    void tryAnswer_이관하면_안내를_남긴다() {
        givenEscalation();
        given(messageMapper.existsSystemMessage(CONVERSATION_ID, ESCALATE_NOTE)).willReturn(false);

        boolean answered = service.tryAnswer(event);

        assertThat(answered).isFalse();
        verify(messageWriter).append(CONVERSATION_ID, ChatSenderType.SYSTEM, null, null, ESCALATE_NOTE);
        // 상태는 그대로 WAITING_AGENT다. 대기열에 남아야 상담사가 이어받는다.
        verify(conversationMapper, never()).markAiHandled(anyLong(), any());
    }

    @Test
    @DisplayName("같은 이관 안내가 이미 있으면 다시 붙이지 않는다 - 같은 주제를 다시 물으면 같은 판정이 나온다")
    void tryAnswer_이관안내는_중복되지_않는다() {
        givenEscalation();
        given(messageMapper.existsSystemMessage(CONVERSATION_ID, ESCALATE_NOTE)).willReturn(true);

        service.tryAnswer(event);

        verify(conversationMapper).lockById(CONVERSATION_ID);
        verify(messageWriter, never()).append(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("반납은 성공 경로에도 필요하다 - 빠뜨리면 stale 창 동안 후속 질문이 무응답이 된다")
    void releaseCall_선점을_되돌린다() {
        service.releaseCall(CONVERSATION_ID);

        verify(conversationMapper).releaseAiCall(CONVERSATION_ID);
    }
}
