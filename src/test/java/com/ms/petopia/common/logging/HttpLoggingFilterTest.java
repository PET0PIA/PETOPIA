package com.ms.petopia.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로깅 필터가 요청/응답 바디를 소비해버리지 않는지 검증한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=c2VjdXJlLXRlc3Qta2V5LXRlc3Qta2V5LXRlc3Qta2V5LXRlc3Qta2V5",
                "jwt.access-token-expiration=3600000",
                "jwt.refresh-token-expiration=1209600000",
                "petopia.toss.secret-key=test-secret"
        })
class HttpLoggingFilterTest {

    @LocalServerPort
    int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    @DisplayName("필터가 바디를 읽어도 Controller의 @RequestBody에 온전히 도착한다")
    void requestBodyReachesController() {
        String requestBody = """
                {"name":"코코","type":"DOG","password":"secret1234"}
                """;

        ResponseEntity<String> response = client().post()
                .uri("/test/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Controller가 받은 바디를 그대로 돌려주므로, 값이 남아있다면 필터가 소비하지 않은 것이다.
        assertThat(response.getBody()).contains("코코").contains("DOG").contains("secret1234");
    }

    @Test
    @DisplayName("ContentCachingResponseWrapper로 감싼 응답 바디가 클라이언트까지 도착한다")
    void responseBodyReachesClient() {
        ResponseEntity<String> response = client().get()
                .uri("/test/data")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"message\":\"pong\"");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    @Test
    @DisplayName("GlobalExceptionHandler가 만든 에러 응답도 그대로 도착한다")
    void errorResponseReachesClient() {
        ResponseEntity<String> response = client().get()
                .uri("/test/error")
                .retrieve()
                .onStatus(status -> true, (req, res) -> { })
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("U001").contains("존재하지 않는 사용자입니다.");
    }

    @Test
    @DisplayName("전달받은 X-Request-Id를 응답에 그대로 이어준다")
    void propagatesInboundRequestId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", "trace-0001");

        ResponseEntity<String> response = client()
                .method(HttpMethod.GET)
                .uri("/test/data")
                .headers(h -> h.addAll(headers))
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("trace-0001");
    }

    @Test
    @DisplayName("요청 로그는 Controller 실행 전에, 응답 로그는 그 뒤에 따로 남는다")
    void logsRequestAndResponseSeparately() {
        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);

        try {
            client().post()
                    .uri("/test/echo")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"name":"코코","password":"secret1234"}
                            """)
                    .retrieve()
                    .toBodilessEntity();
        } finally {
            root.detachAppender(appender);
        }

        List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("/test/echo") || message.contains(CONTROLLER_MARKER))
                .toList();

        int requestLog = indexOfMessageContaining(messages, "HTTP REQ");
        int controllerLog = indexOfMessageContaining(messages, CONTROLLER_MARKER);
        int responseLog = indexOfMessageContaining(messages, "HTTP RES");

        assertThat(requestLog).as("요청 로그가 남아야 한다").isNotNegative();
        assertThat(controllerLog).as("요청 로그는 Controller 실행 전에 남아야 한다").isGreaterThan(requestLog);
        assertThat(responseLog).as("응답 로그는 Controller 실행 후에 남아야 한다").isGreaterThan(controllerLog);

        // 한 건이 두 줄로 쪼개져도 각 줄이 필요한 정보를 온전히 담고 있어야 한다.
        assertThat(messages.get(requestLog))
                .contains("POST /test/echo")
                .contains("\"name\":\"코코\"")
                .contains("\"password\":\"****\"");
        assertThat(messages.get(responseLog))
                .contains("HTTP RES  200 POST /test/echo")
                .contains("\"name\":\"코코\"");
    }

    @Test
    @DisplayName("presigned upload URL은 응답 로그에서 마스킹한다")
    void masksPresignedUploadUrlInResponseLog() {
        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);

        try {
            client().get().uri("/test/upload-url").retrieve().toBodilessEntity();
        } finally {
            root.detachAppender(appender);
        }

        List<String> responseLogs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("HTTP RES") && message.contains("/test/upload-url"))
                .toList();

        assertThat(responseLogs).isNotEmpty();
        assertThat(responseLogs).anySatisfy(message -> assertThat(message).contains("\"uploadUrl\":\"****\""));
        assertThat(responseLogs).noneMatch(message -> message.contains("signature-value"));
    }

    private int indexOfMessageContaining(List<String> messages, String keyword) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).contains(keyword)) {
                return i;
            }
        }
        return -1;
    }

    /** Controller가 실제로 실행된 시점을 로그 순서로 확인하기 위한 표식. */
    private static final String CONTROLLER_MARKER = "test-controller-invoked";

    @TestConfiguration
    @RestController
    static class TestController {

        private static final org.slf4j.Logger log = LoggerFactory.getLogger(TestController.class);

        @PostMapping("/test/echo")
        Map<String, Object> echo(@RequestBody Map<String, Object> body) {
            log.info(CONTROLLER_MARKER);
            return body;
        }

        @GetMapping("/test/data")
        Map<String, String> data() {
            return Map.of("message", "pong");
        }

        @GetMapping("/test/upload-url")
        Map<String, String> uploadUrl() {
            return Map.of("uploadUrl", "https://storage.example/upload?X-Amz-Signature=signature-value");
        }

        @GetMapping("/test/error")
        Map<String, String> error() {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
