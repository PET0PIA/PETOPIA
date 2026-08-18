package com.ms.petopia.api.fair.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * halls 테이블 매핑 객체. 행사(fairs) 하위, 도면 이미지를 보유한다.
 *
 * <p>부스개수 컬럼은 없다 - booth_slots 개수로 자동 도출한다(V1__init.sql 기준).
 *
 * <p>record가 아닌 이유는 {@link Fair}와 동일 - MyBatis 세터 기반 매핑.
 */
@Getter
@Setter
@ToString
public class Hall {

    private Long hallId;

    /** fairs.fair_id */
    private Long fairId;

    /** A홀, 1홀 등 */
    private String name;

    private String floorPlanImageUrl;

    /** 부스 배치(booth_slots) 낙관적 락 버전. 일괄저장 시 이 값이 요청의 expectedVersion과 다르면 거부한다. */
    private Long boothLayoutVersion;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
