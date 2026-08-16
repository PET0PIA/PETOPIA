package com.ms.petopia.api.reservation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 운영일별 사전예약 정원을 조건부 UPDATE 한 문장으로 점유·반납한다.
 *
 * <p>"운영일별 확정 예약 수 ≤ capacity"라는 불변식을 지키는 <b>유일한</b> 장치다.
 * 예전에는 {@code fair_dates}를 {@code FOR UPDATE}로 잠그고 {@code reservations}를
 * {@code COUNT(*)}로 세어 판정했는데, 그 방식은 같은 운영일 요청을 전부 한 줄로 세우는 데다
 * 임계구간 안의 {@code COUNT(*)}가 예약이 쌓일수록 무거워졌다. 오픈 직후 트래픽이 가장 많은
 * 시점과 정확히 반대로 느려지는 특성이었다.
 *
 * <p>지금은 {@code WHERE reserved_count < capacity}와 {@code SET reserved_count + 1}이 같은
 * 문장 안에 있다. 그 사이에 다른 트랜잭션이 끼어들 수 없으므로 애플리케이션 잠금이 필요 없고,
 * 임계구간은 UPDATE 한 문장이 잡는 행 잠금으로 줄어든다.
 *
 * <p><b>호출 규약</b>: {@link #occupy}는 예약 상태를 만드는 트랜잭션 안에서 호출한다.
 * 트랜잭션이 롤백되면 카운터도 함께 롤백되므로 별도 보상이 필요 없다.
 */
@Mapper
public interface ReservationCapacityMapper {

    /**
     * 정원을 1 점유한다.
     *
     * @return 1이면 점유 성공, 0이면 매진(또는 해당 운영일 없음)
     */
    int occupy(
            @Param("fairId") Long fairId,
            @Param("visitDate") LocalDate visitDate
    );

    /**
     * 점유했던 정원을 1 반납한다.
     *
     * <p>{@code reserved_count > 0} 조건이 CHECK 제약 방어이자 중복 반납 방어다.
     * 같은 예약을 두 번 취소 처리해도 카운터가 음수로 내려가지 않는다.
     *
     * @return 1이면 반납 성공, 0이면 이미 0이거나 해당 운영일 없음
     */
    int release(
            @Param("fairId") Long fairId,
            @Param("visitDate") LocalDate visitDate
    );
}
