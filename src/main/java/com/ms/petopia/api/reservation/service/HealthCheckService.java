package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ComponentStatus;
import com.ms.petopia.api.reservation.dto.DbHealth;
import com.ms.petopia.api.reservation.dto.HealthCheckResponse;
import com.ms.petopia.api.reservation.mapper.HealthCheckMapper;
import com.ms.petopia.global.logging.HttpLoggingFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * DB(MyBatis)와 Redis 연결, 그리고 로깅이 의도대로 동작하는지 확인하는 점검 서비스.
 *
 * <p>점검 실패는 예외로 던지지 않고 {@link ComponentStatus}의 {@code up=false}로 표현한다.
 * DB가 죽었을 때 Redis 점검까지 같이 중단되면 "무엇이 문제인지"를 알 수 없기 때문이다.
 * 대신 원인 파악에 필요한 스택트레이스는 로그에 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private static final String MYSQL = "mysql";
    private static final String REDIS = "redis";

    /** 점검용 키는 실제 데이터와 섞이지 않도록 전용 접두사를 쓰고, TTL로 스스로 지워지게 한다. */
    private static final String REDIS_KEY_PREFIX = "petopia:healthcheck:";
    private static final Duration REDIS_KEY_TTL = Duration.ofSeconds(10);

    private final HealthCheckMapper healthCheckMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * DB와 Redis를 모두 점검한다.
     */
    public HealthCheckResponse checkAll() {
        log.info("연결 점검을 시작합니다.");
        HealthCheckResponse response = HealthCheckResponse.of(currentRequestId(), List.of(checkDb(), checkRedis()));
        log.info("연결 점검을 마쳤습니다. up={}", response.up());
        return response;
    }

    public HealthCheckResponse checkDbOnly() {
        return HealthCheckResponse.of(currentRequestId(), List.of(checkDb()));
    }

    public HealthCheckResponse checkRedisOnly() {
        return HealthCheckResponse.of(currentRequestId(), List.of(checkRedis()));
    }

    /**
     * DUAL 조회로 DB 커넥션을 점검한다.
     *
     * <p>MyBatis는 JPA와 달리 기동 시 커넥션을 열지 않는다.
     * 즉 URL/계정이 틀려도 애플리케이션은 정상 기동하고, 첫 쿼리에서야 실패한다.
     * 이 메서드가 그 "첫 쿼리" 역할을 한다.
     */
    public ComponentStatus checkDb() {
        long startedAt = System.currentTimeMillis();
        try {
            DbHealth dbHealth = healthCheckMapper.selectDbHealth();
            String echo = healthCheckMapper.selectEcho("petopia");
            long took = System.currentTimeMillis() - startedAt;

            if (dbHealth == null || dbHealth.getOk() != 1) {
                throw new IllegalStateException("DUAL 조회 결과가 비정상입니다. result=" + dbHealth);
            }
            if (!"petopia".equals(echo)) {
                throw new IllegalStateException("파라미터 바인딩 결과가 다릅니다. echo=" + echo);
            }

            Map<String, Object> details = new HashMap<>();
            details.put("dbName", dbHealth.getDbName());
            details.put("dbVersion", dbHealth.getDbVersion());
            details.put("dbUser", dbHealth.getDbUser());
            details.put("connectionId", dbHealth.getConnectionId());
            details.put("dbNow", dbHealth.getDbNow());
            details.put("appNow", LocalDateTime.now());
            details.put("bindingEcho", echo);

            log.info("DB 점검 성공. db={}, connectionId={}, {}ms",
                    dbHealth.getDbName(), dbHealth.getConnectionId(), took);

            return ComponentStatus.up(MYSQL, took, "SELECT 1 FROM DUAL 성공", details);
        } catch (Exception e) {
            long took = System.currentTimeMillis() - startedAt;
            log.error("DB 점검에 실패했습니다. {}ms", took, e);
            return ComponentStatus.down(MYSQL, took, e);
        }
    }

    /**
     * SET → GET → DEL 왕복으로 Redis 연결을 점검한다.
     *
     * <p>PING만 보내면 커넥션만 확인되고 쓰기 권한이나 직렬화 설정은 확인되지 않는다.
     * 실제로 값을 넣고 되읽어야 의미가 있다.
     */
    public ComponentStatus checkRedis() {
        String key = REDIS_KEY_PREFIX + UUID.randomUUID();
        String expected = "ping-" + System.nanoTime();
        long startedAt = System.currentTimeMillis();
        try {
            stringRedisTemplate.opsForValue().set(key, expected, REDIS_KEY_TTL);
            String actual = stringRedisTemplate.opsForValue().get(key);
            Long ttlSeconds = stringRedisTemplate.getExpire(key);
            long took = System.currentTimeMillis() - startedAt;

            if (!expected.equals(actual)) {
                throw new IllegalStateException("Redis에 쓴 값과 읽은 값이 다릅니다. expected=%s, actual=%s"
                        .formatted(expected, actual));
            }

            Map<String, Object> details = new HashMap<>();
            details.put("key", key);
            details.put("value", actual);
            details.put("ttlSeconds", ttlSeconds);

            log.info("Redis 점검 성공. key={}, ttl={}s, {}ms", key, ttlSeconds, took);

            return ComponentStatus.up(REDIS, took, "SET/GET/DEL 왕복 성공", details);
        } catch (Exception e) {
            long took = System.currentTimeMillis() - startedAt;
            log.error("Redis 점검에 실패했습니다. key={}, {}ms", key, took, e);
            return ComponentStatus.down(REDIS, took, e);
        } finally {
            // 점검 키는 TTL로도 사라지지만, 실패 흔적을 남기지 않도록 즉시 지운다.
            try {
                stringRedisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("Redis 점검 키 삭제에 실패했습니다. key={}", key, e);
            }
        }
    }

    /**
     * 레벨별 로그를 한 번씩 남긴다.
     *
     * <p>프로필마다 설정된 로그 레벨(local=debug, release=info)이 실제로 적용됐는지,
     * 그리고 콘솔 패턴에 requestId가 찍히는지 눈으로 확인하는 용도다.
     *
     * @return 실제로 활성화된 레벨 목록
     */
    public Map<String, Object> writeLogSamples() {
        log.trace("TRACE 레벨 로그입니다.");
        log.debug("DEBUG 레벨 로그입니다. 파라미터 바인딩도 확인합니다. value={}", 42);
        log.info("INFO 레벨 로그입니다.");
        log.warn("WARN 레벨 로그입니다.");
        log.error("ERROR 레벨 로그입니다.", new IllegalStateException("스택트레이스 출력 확인용 예외"));

        Map<String, Object> enabled = new HashMap<>();
        enabled.put("requestId", currentRequestId());
        enabled.put("trace", log.isTraceEnabled());
        enabled.put("debug", log.isDebugEnabled());
        enabled.put("info", log.isInfoEnabled());
        enabled.put("warn", log.isWarnEnabled());
        enabled.put("error", log.isErrorEnabled());
        return enabled;
    }

    /**
     * HttpLoggingFilter가 MDC에 넣어둔 요청 ID.
     * 필터를 타지 않는 경로(테스트 등)에서는 비어 있을 수 있다.
     */
    private String currentRequestId() {
        return Objects.requireNonNullElse(MDC.get(HttpLoggingFilter.REQUEST_ID), "none");
    }
}
