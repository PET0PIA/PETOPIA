package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * DUAL 조회 결과를 담는 DTO.
 *
 * <p>record가 아니라 세터가 있는 클래스인 이유는, MyBatis 기본 매핑이 세터 기반이기 때문이다.
 * record로 받으려면 생성자 자동 매핑에 의존해야 해서 컬럼이 늘어날 때 깨지기 쉽다.
 * 반대로 API 응답 DTO는 불변이 안전하므로 record를 쓴다. ({@link HealthCheckResponse})
 *
 * <p>컬럼명은 snake_case로 내려오지만 {@code map-underscore-to-camel-case: true} 덕분에
 * camelCase 필드로 매핑된다.
 */
@Getter
@Setter
@ToString
public class DbHealth {

    /** DUAL에서 상수 1을 조회한 값. 커넥션이 살아있으면 항상 1이다. */
    private int ok;

    /** DB 서버 기준 현재 시각. 애플리케이션 시각과 비교하면 타임존 설정 오류를 잡을 수 있다. */
    private LocalDateTime dbNow;

    /** 현재 접속한 스키마명. 로컬/운영 DB를 잘못 바라보는 사고를 여기서 확인한다. */
    private String dbName;

    /** MySQL 서버 버전. */
    private String dbVersion;

    /** 이번 쿼리를 수행한 커넥션 ID. 반복 호출 시 값이 바뀌면 커넥션 풀이 돌고 있다는 뜻이다. */
    private Long connectionId;

    /** 접속 계정 (user@host). */
    private String dbUser;
}
