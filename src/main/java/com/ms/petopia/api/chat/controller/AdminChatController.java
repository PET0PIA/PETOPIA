package com.ms.petopia.api.chat.controller;

import com.ms.petopia.api.chat.dto.AdminChatConversationDetailResponse;
import com.ms.petopia.api.chat.dto.AdminChatConversationListResponse;
import com.ms.petopia.api.chat.dto.AdminChatFilter;
import com.ms.petopia.api.chat.dto.SendChatMessageRequest;
import com.ms.petopia.api.chat.service.AdminChatService;
import com.ms.petopia.api.chat.service.ChatStreamService;
import com.ms.petopia.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 상담 콘솔 API.
 *
 * <p>접근 권한은 {@code SecurityConfig}의 {@code /api/admin/**} → SUPER_ADMIN 규칙에 걸린다.
 * 행사 관리자에게도 열어주려면 그 규칙보다 <b>위에</b> 별도 매처를 놓아야 한다(아래에 두면
 * 먼저 잡혀 도달하지 못한다).
 */
@RestController
@RequestMapping("/api/admin/chat")
@RequiredArgsConstructor
public class AdminChatController {

    private final AdminChatService adminChatService;
    private final ChatStreamService chatStreamService;

    /**
     * 대기열 목록.
     *
     * @param filter 생략하면 아직 끝나지 않은 상담만. 상담사가 화면을 여는 목적이 그것이다.
     */
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<AdminChatConversationListResponse>> list(
            @RequestParam(defaultValue = "OPEN") AdminChatFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminChatService.list(filter, page, size)));
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<AdminChatConversationDetailResponse>> detail(
            @PathVariable Long conversationId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminChatService.detail(conversationId)));
    }

    /** 답변 전송. 이 호출이 고객의 입력 잠금을 푼다. */
    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<AdminChatConversationDetailResponse>> reply(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendChatMessageRequest request
    ) {
        AdminChatConversationDetailResponse response =
                adminChatService.reply(conversationId, adminId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, response));
    }

    @PostMapping("/conversations/{conversationId}/assign")
    public ResponseEntity<ApiResponse<Void>> assign(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long conversationId
    ) {
        adminChatService.assign(conversationId, adminId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/conversations/{conversationId}/close")
    public ResponseEntity<ApiResponse<Void>> close(@PathVariable Long conversationId) {
        adminChatService.close(conversationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** 콘솔 배지용 답변 대기 건수. */
    @GetMapping("/unanswered-count")
    public ResponseEntity<ApiResponse<Long>> unansweredCount() {
        return ResponseEntity.ok(ApiResponse.success(adminChatService.countWaiting()));
    }

    /**
     * 입력 중 하트비트. 상담사가 타이핑하는 동안 3초마다 호출한다.
     *
     * <p>하트비트 방식인 이유는 "입력 시작/중단"을 이벤트로 주고받으면 중단 신호가 유실될 때
     * 표시가 영원히 켜진 채 남기 때문이다. 갱신을 멈추면 6초 뒤 저절로 꺼진다.
     */
    @PostMapping("/conversations/{conversationId}/typing")
    public ResponseEntity<ApiResponse<Void>> startTyping(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long conversationId
    ) {
        chatStreamService.updateTyping(conversationId, adminId, true);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 즉시 중단. 전송·포커스 아웃·대화 전환·화면 이탈에서 호출한다.
     *
     * <p>TTL 만료를 기다리지 않고 지우는 이유는, 답변이 이미 도착한 화면에서 "입력 중"이
     * 최대 6초 더 깜빡이는 걸 막기 위해서다.
     */
    @DeleteMapping("/conversations/{conversationId}/typing")
    public ResponseEntity<ApiResponse<Void>> stopTyping(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long conversationId
    ) {
        chatStreamService.updateTyping(conversationId, adminId, false);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
