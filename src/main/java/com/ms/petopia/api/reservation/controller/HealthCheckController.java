package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.HealthCheckResponse;
import com.ms.petopia.api.reservation.service.HealthCheckService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 로깅 필터 / DB(MyBatis) / Redis 연결을 확인하기 위한 점검용 컨트롤러.
 *
 * <p>실제 예약 업무 API가 아니라 인프라 배선 확인용이다. 예약 도메인 구현이 끝나면
 * 이 컨트롤러는 지우거나 actuator health로 대체한다.
 *
 * <p>경로가 {@code /actuator}로 시작하지 않으므로 HttpLoggingFilter의 로깅 대상에 포함된다.
 * 즉 이 API를 호출하는 것만으로 요청/응답 로깅까지 함께 검증된다.
 *
 * <pre>
 * GET  /api/reservation/health          DB + Redis 동시 점검
 * GET  /api/reservation/health/db       DUAL 조회만
 * GET  /api/reservation/health/redis    Redis SET/GET/DEL만
 * GET  /api/reservation/health/logs     레벨별 로그 출력
 * POST /api/reservation/health/echo     요청 바디 로깅/마스킹 확인
 * GET  /api/reservation/health/error    예외 로깅 + GlobalExceptionHandler 확인
 * </pre>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reservation/health")
public class HealthCheckController {

    private final HealthCheckService healthCheckService;

    /**
     * DB와 Redis를 함께 점검한다.
     * 하나라도 실패하면 503을 내려 모니터링에서 바로 걸리게 한다.
     */
    @GetMapping
    public ResponseEntity<HealthCheckResponse> check() {
        return toResponseEntity(healthCheckService.checkAll());
    }

    @GetMapping("/db")
    public ResponseEntity<HealthCheckResponse> checkDb() {
        return toResponseEntity(healthCheckService.checkDbOnly());
    }

    @GetMapping("/redis")
    public ResponseEntity<HealthCheckResponse> checkRedis() {
        return toResponseEntity(healthCheckService.checkRedisOnly());
    }

    /**
     * 레벨별 로그를 남긴다. 콘솔에서 requestId 패턴과 활성 레벨을 함께 확인한다.
     */
    @GetMapping("/logs")
    public Map<String, Object> writeLogs() {
        return healthCheckService.writeLogSamples();
    }

    /**
     * 요청 바디를 그대로 돌려준다.
     *
     * <p>확인 포인트 두 가지.
     * <ul>
     *   <li>필터가 로깅을 위해 바디를 읽어도 {@code @RequestBody}에 온전히 도착하는가</li>
     *   <li>{@code password}, {@code token} 같은 필드가 로그에서 {@code ****}로 마스킹되는가</li>
     * </ul>
     */
    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        log.info("echo 요청을 받았습니다. keys={}", body.keySet());
        return body;
    }

    /**
     * 예외 상황의 로그와 에러 응답 포맷을 확인한다.
     *
     * @param type {@code common}이면 {@link CommonException}(WARN + 4xx),
     *             그 외에는 처리되지 않은 예외(ERROR + 500)를 발생시킨다.
     */
    @GetMapping("/error")
    public void raiseError(@RequestParam(defaultValue = "common") String type) {
        if ("common".equalsIgnoreCase(type)) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "의도적으로 발생시킨 점검용 예외입니다.");
        }
        throw new IllegalStateException("의도적으로 발생시킨 점검용 런타임 예외입니다.");
    }

    private ResponseEntity<HealthCheckResponse> toResponseEntity(HealthCheckResponse response) {
        HttpStatus status = response.up() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(response);
    }
}
