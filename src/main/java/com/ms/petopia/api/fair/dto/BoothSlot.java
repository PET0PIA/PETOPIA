package com.ms.petopia.api.fair.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * booth_slots 테이블 매핑 객체. 도면 위 부스의 "존재"를 정의한다(정의는 도메인2, 점유는 도메인4).
 *
 * <p>pos_x/pos_y/width/height는 도면 대비 비율 좌표(0~1)다. 픽셀 방식은 채택하지 않았다
 * (V1__init.sql 기준, halls에 coordinate_unit 등 픽셀 관련 컬럼 없음).
 *
 * <p>active 필드 타입을 boolean이 아닌 Boolean으로 둔 이유: is_active는 DDL에
 * {@code DEFAULT TRUE}가 걸려 있어, insert 시 값을 안 채우면 DB 기본값이 적용되도록
 * {@code <if test="active != null">}로 조건부 바인딩한다. primitive boolean은 null을
 * 표현할 수 없어 이 조건부 바인딩이 불가능하다({@link Fair#getReservationFee()}와 동일한 이유).
 *
 * <p>필드명을 isActive가 아니라 active로 둔 이유: Lombok이 boolean 필드에서
 * {@code isXxx} 접두어를 특별 취급하는 규칙과 MyBatis의 {@code map-underscore-to-camel-case}가
 * 겹치면 getter/setter 이름이 꼬이기 쉽다(is_active -> isActive 프로퍼티인데 Wrapper 타입
 * Boolean은 getActive()로 생성됨). 그래서 필드는 active로 두고, is_active 컬럼 매핑은
 * XML의 resultMap에서 명시적으로 지정한다({@code BoothSlotMapper.xml} 참고).
 */
@Getter
@Setter
@ToString
public class BoothSlot {

    private Long boothSlotId;

    /** halls.hall_id */
    private Long hallId;

    /** 부스 번호. 예: A-01 */
    private String slotNumber;

    private BigDecimal posX;
    private BigDecimal posY;
    private BigDecimal width;
    private BigDecimal height;

    /** 기본 가격(원) */
    private Long price;

    /** 사용 여부. DDL 기본값 TRUE */
    private Boolean active;

    /** 예: 전기 제공 */
    private String memo;

    /** 위치·번호·가격 변경 제한 시점 */
    private LocalDateTime lockedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
