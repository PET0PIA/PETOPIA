package com.ms.petopia.api.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * SSE 연결용 1회성 티켓.
 *
 * <p><b>왜 필요한가.</b> 브라우저의 {@code EventSource}는 커스텀 헤더를 보낼 수 없다. 그래서
 * 게스트 키를 {@code X-Chat-Guest-Key} 헤더로 넘기는 다른 API와 달리, 스트림만은 소유 증명을
 * URL에 실어야 한다. 그런데 게스트 키를 그대로 쿼리스트링에 넣으면
 * {@code HttpLoggingFilter}가 {@code uri?query} 형태로 로그에 남기고, 프록시 액세스 로그에도
 * 남는다 - 상담 내용을 읽을 수 있는 자격증명이 로그 파일에 영구히 박히는 셈이다.
 *
 * <p>그래서 헤더로 인증된 요청에서 짧은 수명의 티켓을 발급하고, URL에는 그 티켓만 싣는다.
 * 로그에 남더라도 60초가 지났거나 이미 쓰인 값이라 재사용할 수 없다.
 *
 * <p>1회성이라 {@code EventSource}의 자동 재연결과는 맞지 않는다. 클라이언트가 재연결을 직접
 * 관리하며 매번 새 티켓을 받는다.
 */
@Component
@RequiredArgsConstructor
public class ChatStreamTicketStore {

    private static final String KEY_PREFIX = "chat:stream-ticket:";

    /** 발급 직후 연결에 쓰는 값이라 짧게 잡는다. 네트워크가 느린 환경을 감안해 60초. */
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate stringRedisTemplate;

    public String issue(Long conversationId) {
        String ticket = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + ticket, conversationId.toString(), TTL);
        return ticket;
    }

    /**
     * 티켓을 소비하고 대상 대화 ID를 돌려준다.
     *
     * <p>GETDEL로 조회와 삭제를 한 번에 처리한다. 조회 후 삭제로 나누면 같은 티켓으로 들어온
     * 두 요청이 모두 통과할 수 있다.
     *
     * @return 유효하지 않거나 이미 쓰인 티켓이면 null
     */
    public Long consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String value = stringRedisTemplate.opsForValue().getAndDelete(KEY_PREFIX + ticket);
        return value == null ? null : Long.valueOf(value);
    }
}
