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
import com.ms.petopia.api.chat.mapper.ChatMenuClickMapper;
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

import java.time.Duration;
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
     * 진행 중이라고 볼 AI 호출의 최대 나이.
     *
     * <p>이보다 오래된 선점은 없는 것으로 보고 다시 선점한다. 프로세스가 죽어 반납되지 않은
     * 행을 그냥 두면 그 대화의 자동 응대가 영구히 막히는데, 한 번의 배포 사고가 남기기에
     * 너무 긴 흔적이다.
     *
     * <p>3분은 큐 대기(core 2 / max 4 / queue 50)와 Claude 호출을 합쳐 넉넉히 잡은 값이다.
     * 이보다 오래 걸린 호출은 중복을 감수하는 편이 낫다 - 그 시점의 사용자는 이미 답을
     * 포기했을 가능성이 높고, 그렇다면 새 질문에 답하는 쪽이 맞다.
     */
    private static final Duration AI_CALL_STALE_AFTER = Duration.ofMinutes(3);

    private static final String SETTING_GREETING = "GREETING";
    private static final String SETTING_AGENT_RECEIVED = "AGENT_RECEIVED";
    private static final String SETTING_OFFLINE_NOTICE = "OFFLINE_NOTICE";

    private final ChatMenuMapper menuMapper;
    private final ChatMenuClickMapper menuClickMapper;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatSettingMapper settingMapper;
    private final ChatBusinessHourService businessHourService;
    private final ChatTimeProvider timeProvider;
    private final ChatMessageWriter messageWriter;
    private final ClaudeSupportResponder responder;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 위젯을 열었을 때 필요한 것을 한 번에 준다.
     *
     * <p>진행 중인 대화가 있으면 함께 실어 보낸다. 없다고 새 대화를 만들지는 않는다 - 위젯을
     * 열기만 하고 닫는 사용자가 대부분인데 그때마다 빈 대화가 쌓이면 상담사 대기열이 쓰레기로 찬다.
     */
    @Transactional(readOnly = true)
    public ChatBootstrapResponse bootstrap(Long userId, String guestKey) {
        // 고정 답변을 여기서 함께 내린다. 클릭 시 추가 요청이 없어야 "누르면 바로 답변"이
        // 성립한다. 공개 안내문이라 미리 내려도 노출 위험이 없고, 버튼 4~5개 × 200자 내외라
        // 응답 크기도 무시할 수준이다. 버튼이 수십 개로 늘면 클릭 시 조회로 되돌린다.
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

        // hasHistory를 따로 내리는 이유: 프론트가 history.length로 재현하면 나중에 이력
        // 페이지네이션이 붙는 순간(첫 페이지가 비어 있을 수 있다) 판정이 어긋난다.
        return new ChatBootstrapResponse(
                setting(SETTING_GREETING),
                menus,
                businessHourService.isWithinBusinessHours(timeProvider.now()),
                history,
                !history.isEmpty(),
                ongoing);
    }

    /**
     * 상담원 연결을 눌러 대화를 시작한다.
     *
     * <p><b>{@code AGENT} 유형만 받는다.</b> 고정형은 세션을 만들지 않고 위젯이 답변을 즉시
     * 렌더한다. 프론트가 고정형에 이 API를 부르지 않는 것과 별개로 여기서 막는 이유는, 요청을
     * 직접 만들면 그 규칙이 그대로 뚫리기 때문이다 - 유형 판정의 단일 주체는 서버여야 한다.
     *
     * <p>접수 안내만 남기고 {@code BOT}으로 둔다. 아직 질문을 받지 않았으므로 대기열에 올리면
     * 안 된다 - 올리는 시점은 사용자가 질문을 보낸 뒤다.
     *
     * @param guestKey 클라이언트가 이미 갖고 있으면 그대로 쓰고, 없으면 새로 발급한다.
     */
    @Transactional
    public ChatConversationResponse start(String menuCode, Long userId, String guestKey) {
        ChatMenu menu = menuMapper.selectActiveByCode(menuCode);
        if (menu == null) {
            throw new CommonException(ErrorCode.CHAT_MENU_NOT_FOUND);
        }
        if (menu.getAnswerType() != ChatAnswerType.AGENT) {
            throw new CommonException(ErrorCode.CHAT_MENU_NOT_CONNECTABLE);
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

    /** 접수 안내. 상담원 연결만 세션을 만들므로 유형 분기가 없다. */
    private void appendOpeningMessages(ChatConversation conversation, ChatMenu menu) {
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
            // 이제 여기 걸리는 경우는 종료된 대화 하나뿐이다. 상태를 다시 읽어 분기하지 않고
            // 바로 그 오류를 던진다 - 조건부 UPDATE가 실패했다는 사실 자체가 그 판정이다.
            throw new CommonException(ErrorCode.CHAT_ALREADY_CLOSED);
        }

        messageWriter.append(conversationId, ChatSenderType.USER, userId, null, content);
        // 사용자가 다른 탭·기기에서도 같은 대화를 열어둘 수 있다. 상태 변화는 그 화면에도
        // 즉시 반영돼야 한다.
        messageWriter.publishStatus(conversationId, ChatConversationStatus.WAITING_AGENT);

        requestAiAnswerIfEligible(conversation, now);

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
     * <p>조건은 셋이고, 하나라도 어긋나면 그냥 상담사 대기로 둔다.
     * <ol>
     *   <li>응답기가 켜져 있을 것(API 키가 있을 것).</li>
     *   <li><b>상담사가 아직 개입하지 않았을 것</b> - 사람이 답을 시작한 대화에 AI가 다시
     *       끼어들면 두 목소리가 생기고, 최악의 경우 상담사가 한 말을 자동 답변이 뒤집는다.
     *       배정된 상담사가 있다는 건 이미 답변이 나갔다는 뜻이다.</li>
     *   <li>운영시간 밖일 것 - 상담사가 곧 답할 상황에 AI를 끼우면 대화만 중복된다.</li>
     * </ol>
     *
     * <p><b>문의 유형 조건은 없다.</b> 이제 세션을 만드는 유형이 상담원 연결 하나뿐이라,
     * 유형을 다시 확인하는 것은 같은 판정을 두 번 하는 일이다.
     *
     * <p><b>횟수 한도도 없다.</b> 사람이 답할 수 없는 시간에 답을 아낄 이유가 없다. 남은
     * 제약은 진행 중 호출 1건이고, 그건 한도가 아니라 중복 제거다 - 답을 기다리다 같은 질문을
     * 연달아 보내면 호출이 동시에 여러 건 돌고 답변이 순서 없이 쌓인다. 막혔더라도 사용자에게
     * 아무 안내도 하지 않는다. 이미 같은 대화의 답변이 오는 중이므로 기다리면 도착한다.
     *
     * <p>선점까지 성공하면 이벤트만 남기고 끝낸다. 실제 호출은 커밋 뒤 다른 스레드에서 일어난다.
     */
    private void requestAiAnswerIfEligible(ChatConversation conversation, LocalDateTime now) {
        if (!responder.isEnabled()) {
            return;
        }
        if (conversation.getAssignedAdminId() != null) {
            return;
        }
        if (businessHourService.isWithinBusinessHours(now)) {
            return;
        }

        Long conversationId = conversation.getConversationId();
        if (conversationMapper.claimAiCall(conversationId, now, now.minus(AI_CALL_STALE_AFTER)) != 1) {
            return;
        }

        eventPublisher.publishEvent(new ChatAiAnswerRequestedEvent(conversationId));
    }

    /**
     * 고정형 버튼 클릭을 집계에 남긴다. <b>세션도 메시지도 만들지 않는다.</b>
     *
     * <p>답변은 위젯이 이미 갖고 있으므로(bootstrap이 실어 보냈다) 이 호출은 화면과 무관하다.
     * 그래서 프론트는 결과를 기다리지 않고, 실패해도 사용자에게 알리지 않는다.
     *
     * <p>활성 메뉴인지는 확인한다. 지표에 없는 버튼의 클릭이 쌓이면 그 표를 읽는 사람이
     * 설명할 수 없는 행을 보게 되고, FK 위반으로 500이 나가는 것보다는 404가 낫다.
     *
     * <p>유형을 {@code FIXED}로 좁히지 않는다. 상담원 연결 버튼도 얼마나 눌리는지가 지표로
     * 의미 있고, 그쪽은 세션까지 생기므로 두 숫자를 비교하면 "누르고 그만둔 비율"이 보인다.
     */
    @Transactional
    public void logMenuClick(String menuCode, Long userId, String guestKey) {
        ChatMenu menu = menuMapper.selectActiveByCode(menuCode);
        if (menu == null) {
            throw new CommonException(ErrorCode.CHAT_MENU_NOT_FOUND);
        }
        menuClickMapper.insert(menu.getMenuId(), userId, guestKey);
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
