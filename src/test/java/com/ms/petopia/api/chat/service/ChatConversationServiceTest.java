package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.dto.ChatAnswerType;
import com.ms.petopia.api.chat.dto.ChatConversationStatus;
import com.ms.petopia.api.chat.entity.ChatConversation;
import com.ms.petopia.api.chat.entity.ChatMenu;
import com.ms.petopia.api.chat.event.ChatAiAnswerRequestedEvent;
import com.ms.petopia.api.chat.mapper.ChatConversationMapper;
import com.ms.petopia.api.chat.mapper.ChatMenuClickMapper;
import com.ms.petopia.api.chat.mapper.ChatMenuMapper;
import com.ms.petopia.api.chat.mapper.ChatMessageMapper;
import com.ms.petopia.api.chat.mapper.ChatSettingMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/*
 * ChatConversationService 단위 테스트. Mapper는 Mock으로 대체하고 세 가지만 본다.
 *   - 고정형이 상담을 만들지 못하는가(재설계의 전제)
 *   - 클릭 집계가 세션·메시지를 만들지 않는가
 *   - AI 예약 조건이 "운영시간 밖 + 상담사 미개입 + 진행 중 호출 없음"으로 좁혀졌는가
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatConversationServiceTest {

    private static final long CONVERSATION_ID = 100L;
    private static final long MENU_ID = 7L;
    private static final String GUEST_KEY = "11111111-1111-1111-1111-111111111111";
    /** 평일 22시. 시딩 운영시간(평일 09~18) 밖이다. */
    private static final LocalDateTime NIGHT = LocalDateTime.of(2026, 8, 20, 22, 0);

    @Mock private ChatMenuMapper menuMapper;
    @Mock private ChatMenuClickMapper menuClickMapper;
    @Mock private ChatConversationMapper conversationMapper;
    @Mock private ChatMessageMapper messageMapper;
    @Mock private ChatSettingMapper settingMapper;
    @Mock private ChatBusinessHourService businessHourService;
    @Mock private ChatTimeProvider timeProvider;
    @Mock private ChatMessageWriter messageWriter;
    @Mock private ClaudeSupportResponder responder;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private ChatConversationService service;

    @BeforeEach
    void setUp() {
        given(timeProvider.now()).willReturn(NIGHT);
    }

    private ChatMenu menu(ChatAnswerType type) {
        return ChatMenu.builder().menuId(MENU_ID).code("CODE").answerType(type).build();
    }

    private ChatConversation conversation(Long assignedAdminId) {
        return ChatConversation.builder()
                .conversationId(CONVERSATION_ID)
                .guestKey(GUEST_KEY)
                .menuId(MENU_ID)
                .status(ChatConversationStatus.BOT)
                .assignedAdminId(assignedAdminId)
                .build();
    }

    @Test
    @DisplayName("고정형 코드로 상담을 시작하려 하면 거부한다 - 프론트가 부르지 않는 것과 별개로 서버가 판정 주체다")
    void start_고정형이면_거부한다() {
        given(menuMapper.selectActiveByCode("FIXED_CODE")).willReturn(menu(ChatAnswerType.FIXED));

        assertThatThrownBy(() -> service.start("FIXED_CODE", null, GUEST_KEY))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_MENU_NOT_CONNECTABLE);

        verify(conversationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("클릭 집계는 클릭 행 한 건만 남기고 상담도 메시지도 만들지 않는다")
    void logMenuClick_세션을_만들지_않는다() {
        given(menuMapper.selectActiveByCode("FIXED_CODE")).willReturn(menu(ChatAnswerType.FIXED));

        service.logMenuClick("FIXED_CODE", null, GUEST_KEY);

        verify(menuClickMapper).insert(MENU_ID, null, GUEST_KEY);
        verify(conversationMapper, never()).insert(any());
        verify(messageWriter, never()).append(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("내려간 버튼의 클릭은 받지 않는다 - 지표에 없는 버튼의 행이 쌓이면 표를 설명할 수 없다")
    void logMenuClick_비활성메뉴는_거부한다() {
        given(menuMapper.selectActiveByCode("GONE")).willReturn(null);

        assertThatThrownBy(() -> service.logMenuClick("GONE", null, GUEST_KEY))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_MENU_NOT_FOUND);

        verify(menuClickMapper, never()).insert(any(), any(), any());
    }

    @Test
    @DisplayName("운영시간 안에는 AI를 예약하지 않는다 - 상담사가 곧 답할 상황에 끼우면 대화만 중복된다")
    void sendUserMessage_운영시간내에는_이벤트를_발행하지_않는다() {
        givenSendableConversation(null);
        given(businessHourService.isWithinBusinessHours(NIGHT)).willReturn(true);

        service.sendUserMessage(CONVERSATION_ID, "문의합니다", null, GUEST_KEY);

        verify(eventPublisher, never()).publishEvent(any(ChatAiAnswerRequestedEvent.class));
        verify(messageWriter).publishStatus(CONVERSATION_ID, ChatConversationStatus.WAITING_AGENT);
    }

    @Test
    @DisplayName("운영시간 밖이면 AI를 예약한다")
    void sendUserMessage_운영시간외에는_이벤트를_발행한다() {
        givenSendableConversation(null);
        given(businessHourService.isWithinBusinessHours(NIGHT)).willReturn(false);
        given(conversationMapper.claimAiCall(eq(CONVERSATION_ID), eq(NIGHT), any())).willReturn(1);

        service.sendUserMessage(CONVERSATION_ID, "문의합니다", null, GUEST_KEY);

        verify(eventPublisher).publishEvent(new ChatAiAnswerRequestedEvent(CONVERSATION_ID));
    }

    @Test
    @DisplayName("한도가 없으므로 같은 대화에서 반복 질문해도 매번 예약된다 - 선점이 반납된 뒤라면")
    void sendUserMessage_반복질문도_매번_발행된다() {
        givenSendableConversation(null);
        given(businessHourService.isWithinBusinessHours(NIGHT)).willReturn(false);
        given(conversationMapper.claimAiCall(eq(CONVERSATION_ID), eq(NIGHT), any())).willReturn(1);

        for (int i = 0; i < 5; i++) {
            service.sendUserMessage(CONVERSATION_ID, "또 물어봅니다", null, GUEST_KEY);
        }

        verify(eventPublisher, org.mockito.Mockito.times(5))
                .publishEvent(new ChatAiAnswerRequestedEvent(CONVERSATION_ID));
    }

    @Test
    @DisplayName("진행 중인 호출이 있으면 예약하지 않는다 - 한도가 아니라 중복 제거다")
    void sendUserMessage_진행중호출이_있으면_발행하지_않는다() {
        givenSendableConversation(null);
        given(businessHourService.isWithinBusinessHours(NIGHT)).willReturn(false);
        // 선점 실패 = 이 대화에 이미 호출이 돌고 있다.
        given(conversationMapper.claimAiCall(eq(CONVERSATION_ID), eq(NIGHT), any())).willReturn(0);

        service.sendUserMessage(CONVERSATION_ID, "또 물어봅니다", null, GUEST_KEY);

        verify(eventPublisher, never()).publishEvent(any(ChatAiAnswerRequestedEvent.class));
    }

    @Test
    @DisplayName("선점 기준 시각은 현재보다 과거다 - stale 창이 없으면 죽은 선점이 영구히 남는다")
    void sendUserMessage_선점은_stale_기준시각과_함께_요청된다() {
        givenSendableConversation(null);
        given(businessHourService.isWithinBusinessHours(NIGHT)).willReturn(false);
        given(conversationMapper.claimAiCall(eq(CONVERSATION_ID), eq(NIGHT), any())).willReturn(1);

        service.sendUserMessage(CONVERSATION_ID, "문의합니다", null, GUEST_KEY);

        var staleBefore = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(conversationMapper).claimAiCall(eq(CONVERSATION_ID), eq(NIGHT), staleBefore.capture());
        assertThat(staleBefore.getValue()).isBefore(NIGHT);
    }

    @Test
    @DisplayName("상담사가 배정된 대화에는 AI가 끼어들지 않는다 - 사람이 한 말을 자동 답변이 뒤집을 수 있다")
    void sendUserMessage_상담사배정된_대화는_발행하지_않는다() {
        givenSendableConversation(42L);
        given(businessHourService.isWithinBusinessHours(NIGHT)).willReturn(false);

        service.sendUserMessage(CONVERSATION_ID, "문의합니다", null, GUEST_KEY);

        verify(eventPublisher, never()).publishEvent(any(ChatAiAnswerRequestedEvent.class));
        verify(conversationMapper, never()).claimAiCall(anyLong(), any(), any());
    }

    @Test
    @DisplayName("종료된 대화에는 보낼 수 없다 - 조건부 UPDATE 실패가 곧 그 판정이다")
    void sendUserMessage_종료된_대화는_거부한다() {
        given(conversationMapper.selectById(CONVERSATION_ID)).willReturn(conversation(null));
        given(conversationMapper.markWaitingAgent(CONVERSATION_ID, NIGHT)).willReturn(0);

        assertThatThrownBy(() -> service.sendUserMessage(CONVERSATION_ID, "문의합니다", null, GUEST_KEY))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_ALREADY_CLOSED);
    }

    private void givenSendableConversation(Long assignedAdminId) {
        ChatConversation conversation = conversation(assignedAdminId);
        given(conversationMapper.selectById(CONVERSATION_ID)).willReturn(conversation);
        given(conversationMapper.markWaitingAgent(CONVERSATION_ID, NIGHT)).willReturn(1);
        given(responder.isEnabled()).willReturn(true);
    }
}
