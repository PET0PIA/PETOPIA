package com.ms.petopia.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HTTP 요청/응답 로깅 필터.
 *
 * <p>핵심은 "읽어도 소비되지 않게" 만드는 것이다.
 * <ul>
 *   <li>요청: {@link CachedBodyHttpServletRequest}로 감싸 바디를 미리 캐싱한다.
 *       필터가 로깅용으로 읽어도 Controller의 {@code @RequestBody}는 온전한 바디를 받는다.</li>
 *   <li>응답: {@link ContentCachingResponseWrapper}로 감싸 바디를 버퍼에 모은다.
 *       마지막에 {@code copyBodyToResponse()}를 반드시 호출해야 클라이언트에게 실제로 전달된다.</li>
 * </ul>
 *
 * <p>로그는 <b>요청이 들어온 시점</b>과 <b>응답이 나가는 시점</b>에 각각 한 번씩, 총 두 줄로 남긴다.
 * 한 건으로 묶으면 응답이 끝나야 로그가 보이므로, 처리 도중 멈춘 요청(무한 대기, 데드락)이
 * 아예 기록되지 않는 문제가 있다. 두 줄로 나누면 "REQ는 있는데 RES가 없는" 요청을 찾을 수 있다.
 *
 * <p>두 줄은 MDC의 {@code requestId}로 짝을 맞춘다. 동시 요청이 많아 로그가 섞여도
 * 같은 requestId로 grep하면 한 건의 흐름이 복원된다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 로그에 남길 바디 최대 길이. 초과분은 잘라낸다. */
    private static final int MAX_BODY_LENGTH = 2_000;

    /** 메모리에 캐싱할 요청 바디 최대 크기(1MB). 초과 시 캐싱하지 않고 그대로 흘려보낸다. */
    private static final int MAX_CACHE_SIZE = 1024 * 1024;

    /** 로깅 대상에서 제외할 경로 접두사. SSE·파일 다운로드처럼 스트리밍이 필요한 경로도 여기에 추가한다. */
    private static final List<String> EXCLUDED_PATHS = List.of(
            "/actuator", "/favicon.ico", "/css", "/js", "/images", "/static", "/webjars"
    );

    /** 값이 노출되면 안 되는 헤더. */
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie", "proxy-authorization", "x-api-key"
    );

    /**
     * 바디에서 마스킹할 JSON 필드명.
     *
     * <p>{@code content}·{@code lastMessagePreview}·{@code lastMessageContent}는 상담 대화
     * 본문이다. 고객이 환불 사유나 연락처를 적어 보내는 자리라, 그대로 두면 개인정보가 로그
     * 파일에 영구히 남는다. 필드명이 다른 도메인에서도 쓰일 수 있지만, 본문을 가려서 잃는 것보다
     * 남겨서 잃는 쪽이 훨씬 크다.
     */
    private static final String SENSITIVE_FIELD_PATTERN =
            "(?i)\"(password|passwd|pwd|secret|token|accessToken|refreshToken|authorization|uploadUrl"
                    + "|content|lastMessagePreview|lastMessageContent|ticket|guestKey)\"\\s*:\\s*\"[^\"]*\"";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(uri::startsWith);
    }

    /**
     * 스트리밍 응답인지. 응답 <b>래퍼를 씌울지</b>만 결정하고, 필터 자체는 계속 동작한다.
     *
     * <p>필터를 통째로 끄면 안 된다. 이 판단은 클라이언트가 보낸 Accept 헤더에 의존하는데,
     * 아무 요청에나 {@code Accept: text/event-stream}을 붙이면 그 요청은 HTTP 로그도,
     * requestId도, X-Request-Id 응답 헤더도 남지 않는다 - 모든 엔드포인트에 로그 회피
     * 경로가 열린다.
     *
     * <p>미디어 타입은 대소문자를 가리지 않으므로 소문자로 맞춰 비교한다.
     */
    private boolean isServerSentEventRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null
                && accept.toLowerCase(Locale.ROOT).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestId = resolveRequestId(request);
        MDC.put(REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        // 바디 캐싱은 이 생성자 안에서 끝난다. 따라서 아래 logRequest()가 바디를 읽어도
        // 뒤이어 실행될 Controller의 @RequestBody는 온전한 바디를 받는다.
        HttpServletRequest requestToUse = shouldCacheRequestBody(request)
                ? new CachedBodyHttpServletRequest(request)
                : request;

        // SSE 응답만 래퍼를 씌우지 않는다. ContentCachingResponseWrapper는 바디를 버퍼에
        // 모았다가 copyBodyToResponse() 시점에야 내보내는데, 스트리밍에서 그 시점은
        // "연결이 끝날 때"라 이벤트가 실시간으로 전달되지 않는다.
        // 래퍼만 건너뛰고 요청 로그·requestId·응답 헤더는 그대로 남긴다.
        ContentCachingResponseWrapper cachingResponse =
                isServerSentEventRequest(request) ? null : new ContentCachingResponseWrapper(response);
        HttpServletResponse responseToUse = cachingResponse != null ? cachingResponse : response;

        // 요청 로그는 Controller가 실행되기 전에 남긴다.
        // 처리 중 예외로 죽거나 응답이 끝나지 않아도 "요청이 들어왔다"는 사실은 남는다.
        logRequest(requestToUse);

        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(requestToUse, responseToUse);
        } finally {
            long took = System.currentTimeMillis() - startedAt;
            if (!request.isAsyncStarted()) {
                logResponse(requestToUse, responseToUse.getStatus(), cachingResponse, took);
            }
            if (cachingResponse != null) {
                // 버퍼에 모아둔 응답 바디를 실제 응답으로 내보낸다.
                // 이 호출을 빠뜨리면 클라이언트는 빈 바디를 받는다.
                cachingResponse.copyBodyToResponse();
            }
            MDC.remove(REQUEST_ID);
        }
    }

    /**
     * 요청이 들어온 시점의 로그. 아직 처리 결과를 모르므로 항상 INFO로 남긴다.
     */
    private void logRequest(HttpServletRequest request) {
        try {
            String message = "\n"
                    + "┌── HTTP REQ  " + request.getMethod() + ' ' + fullUri(request) + '\n'
                    + "│ FROM : " + clientIp(request) + '\n'
                    + "│ HEAD : " + requestHeaders(request) + '\n'
                    + "│ BODY : " + requestBody(request) + '\n'
                    + "└──";
            log.info(message);
        } catch (Exception e) {
            // 로깅 실패가 실제 요청 처리에 영향을 주면 안 된다.
            log.warn("HTTP 요청 로깅에 실패했습니다. uri={}", request.getRequestURI(), e);
        }
    }

    /**
     * 응답이 나가는 시점의 로그. 상태 코드에 따라 레벨을 올린다.
     *
     * <p>메서드와 URI를 다시 적는 이유는, 응답 로그 한 줄만 봐도 어떤 요청의 결과인지 알 수 있게
     * 하기 위해서다. (URI로 grep하면 요청/응답 두 줄이 함께 잡힌다)
     */
    private void logResponse(HttpServletRequest request, int status,
                             ContentCachingResponseWrapper response, long took) {
        try {
            // 스트리밍 응답은 래퍼가 없어 바디를 알 수 없다. 그래도 상태·소요시간은 남긴다.
            String body = response != null ? responseBody(response) : "(streaming)";
            String message = "\n"
                    + "┌── HTTP RES  " + status + ' ' + request.getMethod() + ' ' + fullUri(request)
                    + " (" + took + "ms)\n"
                    + "│ BODY : " + body + '\n'
                    + "└──";

            if (status >= 500) {
                log.error(message);
            } else if (status >= 400) {
                log.warn(message);
            } else {
                log.info(message);
            }
        } catch (Exception e) {
            log.warn("HTTP 응답 로깅에 실패했습니다. uri={}", request.getRequestURI(), e);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String inbound = request.getHeader(REQUEST_ID_HEADER);
        if (inbound != null && !inbound.isBlank()) {
            return inbound;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String fullUri(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String requestHeaders(HttpServletRequest request) {
        return Collections.list(request.getHeaderNames()).stream()
                .map(name -> name + "=" + (SENSITIVE_HEADERS.contains(name.toLowerCase()) ? "****" : request.getHeader(name)))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private String requestBody(HttpServletRequest request) {
        String contentType = request.getContentType();

        if (contentType == null || request.getContentLengthLong() == 0) {
            // GET처럼 바디가 없는 요청.
            return "[empty]";
        }
        if (isMultipart(contentType)) {
            return "[multipart, length=" + request.getContentLengthLong() + "]";
        }
        if (isFormUrlEncoded(contentType)) {
            // 폼 전송은 컨테이너가 바디를 파싱해 파라미터로 만들기 때문에 파라미터로 남긴다.
            //
            // 주의: 이 호출이 컨테이너의 바디 파싱을 앞당긴다. 파싱된 뒤에는 getInputStream()이
            // 비어 있으므로, form-urlencoded를 @RequestBody(MultiValueMap 등)로 받는 핸들러는
            // 빈 값을 받게 된다. 이 프로젝트의 API는 모두 JSON 바디라 문제되지 않지만,
            // 폼을 @RequestBody로 받아야 한다면 이 분기를 [form, length=N] 표기로 바꿔야 한다.
            return maskSensitive(formatParameters(request.getParameterMap()));
        }
        if (request instanceof CachedBodyHttpServletRequest cached) {
            String body = cached.getBodyAsString();
            return body.isBlank() ? "[empty]" : maskSensitive(truncate(compact(body)));
        }
        return "[not cached, length=" + request.getContentLengthLong() + "]";
    }

    private String responseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length == 0) {
            return "[empty]";
        }
        if (!isTextContentType(response.getContentType())) {
            return "[" + response.getContentType() + ", length=" + content.length + "]";
        }
        String body = new String(content, StandardCharsets.UTF_8);
        return maskSensitive(truncate(compact(body)));
    }

    private String formatParameters(Map<String, String[]> parameterMap) {
        if (parameterMap.isEmpty()) {
            return "[empty]";
        }
        return parameterMap.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * 바디를 통째로 메모리에 올려도 되는 요청인지 판단한다.
     * 텍스트 계열이면서 크기가 크지 않은 경우에만 캐싱한다.
     */
    private boolean shouldCacheRequestBody(HttpServletRequest request) {
        long length = request.getContentLengthLong();
        if (length == 0 || length > MAX_CACHE_SIZE) {
            return false;
        }
        String contentType = request.getContentType();
        // 폼/멀티파트는 컨테이너의 파라미터 파싱을 깨뜨리므로 감싸지 않는다.
        return !isFormUrlEncoded(contentType) && !isMultipart(contentType) && isTextContentType(contentType);
    }

    private boolean isTextContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();
        return lower.startsWith(MediaType.APPLICATION_JSON_VALUE)
                || lower.startsWith("text/")
                || lower.startsWith(MediaType.APPLICATION_XML_VALUE)
                || lower.startsWith("application/problem+json")
                || lower.contains("+json")
                || lower.contains("+xml");
    }

    private boolean isFormUrlEncoded(String contentType) {
        return contentType != null
                && contentType.toLowerCase().startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    private boolean isMultipart(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    private String compact(String body) {
        return body.replaceAll("\\s*\\n\\s*", " ").trim();
    }

    private String truncate(String body) {
        if (body.length() <= MAX_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_BODY_LENGTH) + "... (총 " + body.length() + "자)";
    }

    private String maskSensitive(String body) {
        return body.replaceAll(SENSITIVE_FIELD_PATTERN, "\"$1\":\"****\"");
    }
}
