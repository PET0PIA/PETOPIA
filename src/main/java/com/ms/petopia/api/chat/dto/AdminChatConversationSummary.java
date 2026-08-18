package com.ms.petopia.api.chat.dto;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 대기열 목록에 그릴 한 줄.
 *
 * @param waitingSeconds 마지막 메시지 이후 지난 시간. 상담사가 "누가 제일 오래 기다렸나"를
 *                       한눈에 보게 하는 값이라, 시각이 아니라 경과 시간으로 내려준다
 *                       (브라우저 시계가 틀어져 있어도 순서가 흔들리지 않는다).
 * @param requesterType  회원인지 비회원인지만 알린다. 상담사에게 필요한 건 응대 맥락이지
 *                       사용자 식별정보가 아니다.
 */
public record AdminChatConversationSummary(
        Long conversationId,
        ChatConversationStatus status,
        String menuLabel,
        String requesterType,
        String lastMessagePreview,
        ChatSenderType lastMessageSenderType,
        LocalDateTime lastMessageAt,
        long waitingSeconds,
        boolean assigned
) {

    /** 미리보기 길이. 목록 한 줄에 들어갈 만큼만 자른다. */
    private static final int PREVIEW_LENGTH = 60;

    public static AdminChatConversationSummary of(AdminChatConversationRow row, LocalDateTime now) {
        return new AdminChatConversationSummary(
                row.getConversationId(),
                row.getStatus(),
                row.getMenuLabel(),
                row.getUserId() != null ? "MEMBER" : "GUEST",
                preview(row.getLastMessageContent()),
                row.getLastMessageSenderType(),
                row.getLastMessageAt(),
                waitingSeconds(row.getLastMessageAt(), now),
                row.getAssignedAdminId() != null);
    }

    private static String preview(String content) {
        if (content == null) {
            return "";
        }
        // 줄바꿈을 공백으로 바꾼다. 목록은 한 줄짜리라 개행이 그대로 들어가면 레이아웃이 깨진다.
        String flattened = content.replaceAll("\\s+", " ").trim();
        return flattened.length() <= PREVIEW_LENGTH
                ? flattened
                : flattened.substring(0, PREVIEW_LENGTH) + "…";
    }

    private static long waitingSeconds(LocalDateTime lastMessageAt, LocalDateTime now) {
        if (lastMessageAt == null) {
            return 0;
        }
        long seconds = Duration.between(lastMessageAt, now).getSeconds();
        return Math.max(seconds, 0);
    }
}
