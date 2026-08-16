package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.dto.AdminChatConversationDetailResponse;
import com.ms.petopia.api.chat.dto.AdminChatConversationListResponse;
import com.ms.petopia.api.chat.dto.AdminChatConversationRow;
import com.ms.petopia.api.chat.dto.AdminChatConversationSummary;
import com.ms.petopia.api.chat.dto.AdminChatFilter;
import com.ms.petopia.api.chat.dto.ChatConversationStatus;
import com.ms.petopia.api.chat.dto.ChatSenderType;
import com.ms.petopia.api.chat.entity.ChatConversation;
import com.ms.petopia.api.chat.entity.ChatMessage;
import com.ms.petopia.api.chat.mapper.AdminChatMapper;
import com.ms.petopia.api.chat.mapper.ChatConversationMapper;
import com.ms.petopia.api.chat.mapper.ChatMessageMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상담사 콘솔.
 *
 * <p>고객 쪽 서비스와 대칭이 아닌 점이 하나 있다: <b>상담사에게는 잠금 규칙이 없다.</b>
 * 잠금은 "AI가 답한 뒤 사용자가 더 묻지 못하게" 하는 장치라 고객 입력에만 걸린다.
 * 상담사는 오히려 잠긴 대화를 풀어주는 쪽이다.
 */
@Service
@RequiredArgsConstructor
public class AdminChatService {

    private final AdminChatMapper adminChatMapper;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatMessageWriter messageWriter;
    private final ChatTypingStore typingStore;
    private final ChatStreamService streamService;
    private final ChatTimeProvider timeProvider;

    @Transactional(readOnly = true)
    public AdminChatConversationListResponse list(AdminChatFilter filter, int page, int size) {
        long totalElements = adminChatMapper.countConversations(filter);
        List<AdminChatConversationRow> rows =
                adminChatMapper.selectConversations(filter, (long) page * size, size);

        LocalDateTime now = timeProvider.now();
        List<AdminChatConversationSummary> items = rows.stream()
                .map(row -> AdminChatConversationSummary.of(row, now))
                .toList();

        int totalPages = (int) ((totalElements + size - 1) / size);
        return new AdminChatConversationListResponse(
                items, page, size, totalElements, totalPages, page + 1 < totalPages);
    }

    @Transactional(readOnly = true)
    public AdminChatConversationDetailResponse detail(Long conversationId) {
        ChatConversation conversation = load(conversationId);
        List<ChatMessage> messages = messageMapper.selectByConversation(
                conversationId, null, ChatConversationService.MESSAGE_PAGE_SIZE);
        return AdminChatConversationDetailResponse.of(
                conversation, adminChatMapper.selectMenuLabel(conversationId), messages);
    }

    @Transactional(readOnly = true)
    public long countWaiting() {
        return adminChatMapper.countWaiting();
    }

    /**
     * 상담사가 답변한다. 이 호출이 고객의 잠금을 푼다.
     *
     * <p>상태 전이를 먼저 시도하고 반영 행 수로 판정한다. 조회로 확인한 뒤 저장하면, 그 사이
     * 고객이 상담을 종료했을 때 종료된 대화에 답변이 붙는다.
     */
    @Transactional
    public AdminChatConversationDetailResponse reply(Long conversationId, Long adminId, String content) {
        load(conversationId);

        if (conversationMapper.markInProgress(conversationId, adminId, timeProvider.now()) != 1) {
            throw new CommonException(ErrorCode.CHAT_ALREADY_CLOSED);
        }

        messageWriter.append(conversationId, ChatSenderType.AGENT, adminId, null, content);
        messageWriter.publishStatus(conversationId, ChatConversationStatus.IN_PROGRESS);

        // 답변을 보냈다는 건 입력이 끝났다는 뜻이다. 하트비트 TTL(6초)을 기다리면 답변이 이미
        // 도착한 화면에서 "입력 중"이 몇 초 더 깜빡인다.
        typingStore.clearTyping(conversationId);
        streamService.updateTyping(conversationId, adminId, false);

        return detail(conversationId);
    }

    /** 대화를 맡는다. 이미 배정된 대화는 그대로 둔다(먼저 잡은 상담사 유지). */
    @Transactional
    public void assign(Long conversationId, Long adminId) {
        ChatConversation conversation = load(conversationId);
        if (conversation.getAssignedAdminId() != null) {
            return;
        }
        conversationMapper.markInProgress(conversationId, adminId, timeProvider.now());
    }

    /**
     * 상담사가 상담을 종료한다.
     *
     * <p>고객 화면도 즉시 닫혀야 하므로 상태 이벤트를 함께 보낸다. 이게 없으면 고객은 이미
     * 종료된 대화에 계속 입력하다가 전송 시점에야 거부당한다.
     */
    @Transactional
    public void close(Long conversationId) {
        load(conversationId);
        if (conversationMapper.markClosed(conversationId, timeProvider.now()) == 1) {
            messageWriter.publishStatus(conversationId, ChatConversationStatus.CLOSED);
        }
    }

    private ChatConversation load(Long conversationId) {
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new CommonException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }
}
