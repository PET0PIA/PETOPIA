package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.dto.ChatAnswerType;
import com.ms.petopia.api.chat.dto.ChatBootstrapResponse;
import com.ms.petopia.api.chat.dto.ChatConversationResponse;
import com.ms.petopia.api.chat.dto.ChatConversationStatus;
import com.ms.petopia.api.chat.dto.ChatMenuResponse;
import com.ms.petopia.api.chat.dto.ChatMessageResponse;
import com.ms.petopia.api.chat.dto.ChatSenderType;
import com.ms.petopia.api.chat.entity.ChatConversation;
import com.ms.petopia.api.chat.entity.ChatMenu;
import com.ms.petopia.api.chat.entity.ChatMessage;
import com.ms.petopia.api.chat.mapper.ChatConversationMapper;
import com.ms.petopia.api.chat.mapper.ChatMenuMapper;
import com.ms.petopia.api.chat.mapper.ChatMessageMapper;
import com.ms.petopia.api.chat.mapper.ChatSettingMapper;
import com.ms.petopia.api.chat.event.ChatAiAnswerRequestedEvent;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 상담 대화의 생성·조회·전송을 담당한다.
 *
 * <p>이 클래스가 지키는 불변식은 하나다: <b>사용자가 메시지를 보낼 수 있는지는 서버만 판단한다.</b>
 * 프론트의 입력창 비활성화는 거들 뿐이고, 실제 차단은 여기 조건부 UPDATE에서 일어난다.
 */
@Service
@RequiredArgsConstructor
public class ChatConversationService {

    /** 한 번에 내려줄 메시지 상한. 한 상담이 이보다 길어지면 커서로 이어 받는다. */
    static final int MESSAGE_PAGE_SIZE = 100;

    /**
     * 위젯을 열 때 불러올 지난 메시지 수(여러 상담 합산).
     *
     * <p>전부 불러오지 않는 이유는 첫 렌더 비용이다. 사용자가 스크롤로 확인하는 범위는
     * 보통 최근 몇 건이고, 더 옛날 것은 지금 화면에서 필요하지 않다.
     */
    private static final int HISTORY_PAGE_SIZE = 50;

    /**
     * 대화당 AI 답변 허용 횟수.
     *
     * <p>마지막 한 번을 답한 뒤 자동 답변을 닫고 입력을 잠근다. 그 전까지는 잠그지 않으므로
     * 사용자는 이어서 물어볼 수 있다.
     *
     * <p><b>이 한도만으로는 상한이 되지 못한다.</b> 카운터가 대화 행에 있어서 상담을 종료하고
     * 새로 시작하면 0부터 다시 센다. 실제 상한은 {@link ChatAiRateLimiter}의 시간창이 만든다 -
     * 이 값을 바꾸면 거기 {@code HOURLY_LIMIT}도 같이 맞춰야 한다.
     */
    private static final int AI_ANSWER_LIMIT = 3;

    private static final String SETTING_GREETING = "GREETING";
    private static final String SETTING_AGENT_RECEIVED = "AGENT_RECEIVED";
    private static final String SETTING_OFFLINE_NOTICE = "OFFLINE_NOTICE";

    private final ChatMenuMapper menuMapper;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatSettingMapper settingMapper;
    private final ChatBusinessHourService businessHourService;
    private final ChatTimeProvider timeProvider;
    private final ChatMessageWriter messageWriter;
    private final ClaudeSupportResponder responder;
    private final ChatAiRateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 위젯을 열었을 때 필요한 것을 한 번에 준다.
     *
     * <p>진행 중인 대화가 있으면 함께 실어 보낸다. 없다고 새 대화를 만들지는 않는다 - 위젯을
     * 열기만 하고 닫는 사용자가 대부분인데 그때마다 빈 대화가 쌓이면 상담사 대기열이 쓰레기로 찬다.
     */
    @Transactional(readOnly = true)
    public ChatBootstrapResponse bootstrap(Long userId, String guestKey) {
        List<ChatMenuResponse> menus = menuMapper.selectActiveMenus().stream()
                .map(ChatMenuResponse::from)
                .toList();

        ChatConversationResponse ongoing = null;
        List<ChatMessageResponse> history = List.of();
        if (userId != null || guestKey != null) {
            ChatConversation conversation = conversationMapper.selectLatest(userId, guestKey);
            // 소유 검증을 한 번 더 한다. 조회 조건에 이미 소유자가 들어가 있지만, 조건이
            // 나중에 바뀌어도 남의 대화가 새어나가지 않게 하는 마지막 방어선이다.
            if (conversation != null && conversation.isOwnedBy(userId, guestKey)) {
                // 메시지는 history가 담으므로 여기서는 상태만 만든다(같은 내용을 두 번 싣지 않는다).
                ongoing = ChatConversationResponse.of(conversation, List.of(), null);
            }
            history = ChatMessageResponse.fromAll(
                    messageMapper.selectRecentByRequester(userId, guestKey, HISTORY_PAGE_SIZE));
        }

        return new ChatBootstrapResponse(
                setting(SETTING_GREETING),
                menus,
                businessHourService.isWithinBusinessHours(timeProvider.now()),
                history,
                ongoing);
    }

    /**
     * 문의 유형 버튼을 눌러 대화를 시작한다.
     *
     * <p>유형에 따라 시작 상태가 다르다.
     * <ul>
     *   <li>{@code FIXED} - 저장된 답변을 바로 붙이고 {@code BOT}으로 둔다. 사용자는 이어서
     *       자유롭게 질문할 수 있다.</li>
     *   <li>{@code AI}/{@code AGENT} - 접수 안내만 남기고 {@code BOT}으로 둔다. 아직 질문을
     *       받지 않았으므로 여기서 잠그면 안 된다 - 잠그는 시점은 사용자가 질문을 보낸 뒤다.</li>
     * </ul>
     *
     * @param guestKey 클라이언트가 이미 갖고 있으면 그대로 쓰고, 없으면 새로 발급한다.
     */
    @Transactional
    public ChatConversationResponse start(String menuCode, Long userId, String guestKey) {
        ChatMenu menu = menuMapper.selectActiveByCode(menuCode);
        if (menu == null) {
            throw new CommonException(ErrorCode.CHAT_MENU_NOT_FOUND);
        }

        // 로그인 사용자에게도 게스트 키를 발급해 둔다. 상담 도중 로그아웃하거나 세션이
        // 끊겨도 같은 대화를 이어 볼 수 있어야 하기 때문이다.
        String issuedGuestKey = (guestKey != null && !guestKey.isBlank())
                ? guestKey
                : UUID.randomUUID().toString();

        ChatConversation conversation = ChatConversation.builder()
                .userId(userId)
                .guestKey(issuedGuestKey)
                .menuId(menu.getMenuId())
                .status(ChatConversationStatus.BOT)
                .build();
        conversationMapper.insert(conversation);

        appendOpeningMessages(conversation, menu);
        conversationMapper.touchLastMessageAt(conversation.getConversationId(), timeProvider.now());

        ChatConversation reloaded = conversationMapper.selectById(conversation.getConversationId());
        return toResponse(reloaded, issuedGuestKey);
    }

    /** 유형별 첫 메시지. 고정 답변이면 답변을, 상담사 연결이면 접수 안내를 남긴다. */
    private void appendOpeningMessages(ChatConversation conversation, ChatMenu menu) {
        if (menu.getAnswerType() == ChatAnswerType.FIXED) {
            messageWriter.append(conversation.getConversationId(), ChatSenderType.BOT, null,
                    menu.getMenuId(), menu.getFixedAnswer());
            return;
        }

        messageWriter.append(conversation.getConversationId(), ChatSenderType.SYSTEM, null,
                menu.getMenuId(), setting(SETTING_AGENT_RECEIVED));

        // 운영시간 밖이면 그 사실을 먼저 알린다. 답변이 늦는 이유를 모른 채 기다리는 것이
        // 사용자가 가장 답답해하는 상황이다.
        if (!businessHourService.isWithinBusinessHours(timeProvider.now())) {
            messageWriter.append(conversation.getConversationId(), ChatSenderType.SYSTEM, null,
                    null, setting(SETTING_OFFLINE_NOTICE));
        }
    }

    /**
     * 사용자가 자유 질문을 보낸다.
     *
     * <p>보낼 수 있는 상태인지 확인하는 것과 대기 상태로 바꾸는 것을 하나의 UPDATE로 묶는다.
     * 조회로 먼저 확인하면 그 사이에 들어온 두 번째 요청이 같은 검사를 통과해, 대화 하나에
     * 사용자 질문이 두 건 접수된다.
     */
    @Transactional
    public ChatConversationResponse sendUserMessage(Long conversationId,
                                                    String content,
                                                    Long userId,
                                                    String guestKey) {
        ChatConversation conversation = loadOwned(conversationId, userId, guestKey);

        LocalDateTime now = timeProvider.now();
        if (conversationMapper.markWaitingAgent(conversationId, now) != 1) {
            // 여기 걸리는 경우는 둘뿐이다(AI_ANSWERED, CLOSED). 둘 다 "지금은 못 보낸다"지만
            // 하나는 상담사 답변을 기다리면 풀리고 다른 하나는 영영 풀리지 않아,
            // 사용자가 할 행동이 다르다.
            throw new CommonException(conversation.getStatus() == ChatConversationStatus.CLOSED
                    ? ErrorCode.CHAT_ALREADY_CLOSED
                    : ErrorCode.CHAT_AWAITING_AGENT);
        }

        messageWriter.append(conversationId, ChatSenderType.USER, userId, null, content);
        // 사용자가 다른 탭·기기에서도 같은 대화를 열어둘 수 있다. 잠금은 그 화면에도 즉시 걸려야 한다.
        messageWriter.publishStatus(conversationId, ChatConversationStatus.WAITING_AGENT);

        requestAiAnswerIfEligible(conversation, userId, guestKey);

        // 게스트로 시작한 대화에 로그인 상태로 메시지를 보냈다면 이 시점에 승계한다.
        if (userId != null && conversation.getUserId() == null) {
            conversationMapper.claimByUser(conversationId, userId, guestKey);
        }

        ChatConversation reloaded = conversationMapper.selectById(conversationId);
        return toResponse(reloaded, null);
    }

    /**
     * 조건이 맞으면 AI 답변을 예약한다.
     *
     * <p>조건은 넷이고, 하나라도 어긋나면 그냥 상담사 대기로 둔다.
     * <ol>
     *   <li>문의 유형이 {@code AI}일 것 - 고정 답변·단순 연결 유형은 AI를 쓰지 않는다.</li>
     *   <li><b>상담사가 아직 개입하지 않았을 것</b> - 사람이 답을 시작한 대화에 AI가 다시
     *       끼어들면 두 목소리가 생기고, 최악의 경우 상담사가 한 말을 자동 답변이 뒤집는다.
     *       배정된 상담사가 있다는 건 이미 답변이 나갔다는 뜻이다.</li>
     *   <li>운영시간 밖일 것 - 상담사가 곧 답할 상황에 AI를 끼우면 대화만 중복된다.</li>
     *   <li>요청자의 하루 한도가 남아 있을 것.</li>
     *   <li>대화당 한도를 선점할 수 있을 것 - 이 선점이 한도의 실제 집행 지점이다.</li>
     * </ol>
     *
     * <p>선점까지 성공하면 이벤트만 남기고 끝낸다. 실제 호출은 커밋 뒤 다른 스레드에서 일어난다.
     */
    private void requestAiAnswerIfEligible(ChatConversation conversation, Long userId, String guestKey) {
        if (conversation.getMenuId() == null || !responder.isEnabled()) {
            return;
        }
        if (conversation.getAssignedAdminId() != null) {
            return;
        }

        ChatMenu menu = menuMapper.selectById(conversation.getMenuId());
        if (menu == null || menu.getAnswerType() != ChatAnswerType.AI) {
            return;
        }
        if (businessHourService.isWithinBusinessHours(timeProvider.now())) {
            return;
        }

        String requesterKey = userId != null ? "u:" + userId : "g:" + guestKey;
        if (!rateLimiter.tryConsume(requesterKey)) {
            return;
        }

        Long conversationId = conversation.getConversationId();
        if (conversationMapper.claimAiAnswer(conversationId, AI_ANSWER_LIMIT) != 1) {
            return;
        }

        // 선점 직후 값을 읽어 이번이 마지막 답변인지 판단한다. 같은 트랜잭션 안이라
        // 방금 올린 값이 그대로 보인다.
        Integer used = conversationMapper.selectAiAnswerCount(conversationId);
        boolean lastAnswer = used != null && used >= AI_ANSWER_LIMIT;

        eventPublisher.publishEvent(
                new ChatAiAnswerRequestedEvent(conversationId, menu.getAiContext(), lastAnswer));
    }

    /** 폴링용 조회. {@code afterMessageId} 이후의 메시지만 준다. */
    @Transactional(readOnly = true)
    public ChatConversationResponse getConversation(Long conversationId,
                                                    Long afterMessageId,
                                                    Long userId,
                                                    String guestKey) {
        ChatConversation conversation = loadOwned(conversationId, userId, guestKey);
        List<ChatMessage> messages =
                messageMapper.selectByConversation(conversationId, afterMessageId, MESSAGE_PAGE_SIZE);
        return ChatConversationResponse.of(conversation, messages, null);
    }

    /** 사용자가 상담을 끝낸다. 이미 끝난 상담을 다시 끝내도 오류로 만들지 않는다(멱등). */
    @Transactional
    public void close(Long conversationId, Long userId, String guestKey) {
        loadOwned(conversationId, userId, guestKey);
        if (conversationMapper.markClosed(conversationId, timeProvider.now()) == 1) {
            messageWriter.publishStatus(conversationId, ChatConversationStatus.CLOSED);
        }
    }

    /**
     * 대화를 읽고 소유자인지 확인한다.
     *
     * <p>없는 대화와 남의 대화를 다른 코드로 구분해 응답한다. 존재 여부를 숨기는 편이 더
     * 안전한 도메인도 있지만, 상담 ID는 순번이라 어차피 존재는 추측 가능하고 - 정작 막아야 할
     * 것은 내용 열람이다.
     */
    private ChatConversation loadOwned(Long conversationId, Long userId, String guestKey) {
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new CommonException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
        }
        if (!conversation.isOwnedBy(userId, guestKey)) {
            throw new CommonException(ErrorCode.CHAT_ACCESS_DENIED);
        }
        return conversation;
    }

    private ChatConversationResponse toResponse(ChatConversation conversation, String issuedGuestKey) {
        List<ChatMessage> messages = messageMapper.selectByConversation(
                conversation.getConversationId(), null, MESSAGE_PAGE_SIZE);
        return ChatConversationResponse.of(conversation, messages, issuedGuestKey);
    }

    /**
     * 운영 문구를 읽는다.
     *
     * <p>키가 비어 있어도 상담이 멈추면 안 되므로 빈 문자열로 흘린다 - 문구가 없는 것은
     * 불편이지만, 여기서 예외를 던지면 위젯 자체가 열리지 않는다.
     */
    private String setting(String key) {
        String value = settingMapper.selectValue(key);
        return value != null ? value : "";
    }
}
