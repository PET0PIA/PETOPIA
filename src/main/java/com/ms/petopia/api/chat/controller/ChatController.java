package com.ms.petopia.api.chat.controller;

import com.ms.petopia.api.chat.dto.ChatBootstrapResponse;
import com.ms.petopia.api.chat.dto.ChatConversationResponse;
import com.ms.petopia.api.chat.dto.SendChatMessageRequest;
import com.ms.petopia.api.chat.dto.StartConversationRequest;
import com.ms.petopia.api.chat.service.ChatConversationService;
import com.ms.petopia.api.chat.service.ChatStreamService;
import com.ms.petopia.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 고객용 상담 챗봇 API.
 *
 * <p>비로그인 사용자가 대부분이라 전 구간 permitAll이다. 대신 대화 소유는
 * {@code X-Chat-Guest-Key} 헤더로 증명한다 - 이 값은 서버가 대화를 만들 때 발급한
 * UUIDv4이고, 서비스 계층이 대화에 저장된 값과 일치하는지 매번 검증한다.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    /** 게스트 소유 증명 헤더. 프론트는 이 값을 localStorage에 보관한다. */
    public static final String GUEST_KEY_HEADER = "X-Chat-Guest-Key";

    private final ChatConversationService chatConversationService;
    private final ChatStreamService chatStreamService;

    /**
     * SSE 연결에 쓸 1회성 티켓을 발급한다.
     *
     * <p>EventSource가 커스텀 헤더를 못 보내기 때문에 존재하는 단계다. 자세한 배경은
     * {@link com.ms.petopia.api.chat.service.ChatStreamTicketStore} 참고.
     */
    @PostMapping("/conversations/{conversationId}/stream-ticket")
    public ResponseEntity<ApiResponse<StreamTicketResponse>> issueStreamTicket(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(value = GUEST_KEY_HEADER, required = false) String guestKey,
            @PathVariable Long conversationId
    ) {
        String ticket = chatStreamService.issueTicket(conversationId, userId, guestKey);
        return ResponseEntity.ok(ApiResponse.success(new StreamTicketResponse(ticket)));
    }

    /**
     * 실시간 채널. 새 메시지·상담사 타이핑·상태 변경이 흐른다.
     *
     * <p>응답을 {@code ApiResponse}로 감싸지 않는다 - SSE는 표준 포맷이 정해진 스트림이라
     * 봉투를 씌우면 EventSource가 해석하지 못한다.
     */
    @GetMapping(value = "/conversations/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable Long conversationId,
                                             @RequestParam String ticket) {
        // 거부는 본문 없이 상태 코드로만 답한다. EventSource는 Accept: text/event-stream만
        // 받아들여 JSON 에러 본문을 협상할 수 없고, 본문을 읽지도 못한다.
        return chatStreamService.subscribe(conversationId, ticket)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    public record StreamTicketResponse(String ticket) {
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<ApiResponse<ChatBootstrapResponse>> bootstrap(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(value = GUEST_KEY_HEADER, required = false) String guestKey
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(chatConversationService.bootstrap(userId, guestKey)));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ChatConversationResponse>> start(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(value = GUEST_KEY_HEADER, required = false) String guestKey,
            @Valid @RequestBody StartConversationRequest request
    ) {
        ChatConversationResponse response =
                chatConversationService.start(request.menuCode(), userId, guestKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, response));
    }

    /**
     * 폴링 조회.
     *
     * @param afterMessageId 마지막으로 받은 메시지 ID. 생략하면 처음부터 준다.
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<ChatConversationResponse>> get(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(value = GUEST_KEY_HEADER, required = false) String guestKey,
            @PathVariable Long conversationId,
            @RequestParam(required = false) Long afterMessageId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                chatConversationService.getConversation(conversationId, afterMessageId, userId, guestKey)));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<ChatConversationResponse>> sendMessage(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(value = GUEST_KEY_HEADER, required = false) String guestKey,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendChatMessageRequest request
    ) {
        ChatConversationResponse response = chatConversationService.sendUserMessage(
                conversationId, request.content(), userId, guestKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, response));
    }

    @PostMapping("/conversations/{conversationId}/close")
    public ResponseEntity<ApiResponse<Void>> close(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(value = GUEST_KEY_HEADER, required = false) String guestKey,
            @PathVariable Long conversationId
    ) {
        chatConversationService.close(conversationId, userId, guestKey);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
