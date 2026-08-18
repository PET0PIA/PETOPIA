package com.ms.petopia.global.web;

import com.ms.petopia.api.reservation.service.WaitingRoomService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

import static org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;

/**
 * 대기열을 켠 행사의 예약·결제 요청에 유효한 대기 토큰을 요구한다.
 *
 * <p>토큰은 {@code X-Waiting-Token} 헤더로 받는다. 통과시킬 때 슬롯 수명을 함께 연장하므로,
 * 예약을 만들고 결제를 마칠 때까지 슬롯 하나로 끝난다.
 *
 * <p><b>토큰과 인증 주체가 일치하는지 반드시 확인한다.</b> 안 그러면 토큰 하나를 공유해
 * 여러 계정이 게이트를 통과할 수 있어 유입 제한이 무의미해진다.
 *
 * <p>Redis 장애 시에는 전부 통과시킨다 — 판단은 {@link WaitingRoomService}가 하고,
 * 이 인터셉터는 그 결과를 따르기만 한다.
 */
@RequiredArgsConstructor
public class WaitingRoomInterceptor implements HandlerInterceptor {

    public static final String WAITING_TOKEN_HEADER = "X-Waiting-Token";

    private final WaitingRoomService waitingRoomService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long userId = authenticatedUserId();
        if (userId == null) {
            // 인증은 SecurityFilterChain이 이미 요구했다. 여기서 401을 다시 판단하지 않는다.
            return true;
        }

        Long fairId = pathFairId(request);
        if (fairId == null) {
            // 어느 행사의 슬롯을 대조해야 하는지 알 수 없으면 판정하지 않는다.
            // 등록된 경로는 전부 {fairId}를 갖고 있으므로 정상 흐름에서는 도달하지 않는다.
            return true;
        }

        String token = request.getHeader(WAITING_TOKEN_HEADER);
        if (!waitingRoomService.admit(fairId, token, userId)) {
            throw new CommonException(ErrorCode.WAITING_ROOM_TICKET_REQUIRED);
        }
        return true;
    }

    private Long authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return null;
        }
        return userId;
    }

    /** 경로 변수 {@code {fairId}}를 꺼낸다. 없으면 판정 대상이 아니다. */
    private Long pathFairId(HttpServletRequest request) {
        Object attribute = request.getAttribute(URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> variables)) {
            return null;
        }
        Object fairId = variables.get("fairId");
        if (fairId == null) {
            return null;
        }
        try {
            return Long.valueOf(fairId.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
