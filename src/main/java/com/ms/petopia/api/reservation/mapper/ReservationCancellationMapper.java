package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.ReservationCancellationContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ReservationCancellationMapper {

    ReservationCancellationContext selectCancellationContextForUpdate(
            @Param("reservationId") Long reservationId
    );

    int cancelReservation(
            @Param("reservationId") Long reservationId,
            @Param("expectedStatus") String expectedStatus,
            @Param("reason") String reason,
            @Param("canceledBy") Long canceledBy,
            @Param("now") LocalDateTime now
    );

    /**
     * 취소 이력을 남긴다. 예약금을 환불한 취소면 refundId/refundAmount가 함께 기록돼,
     * 이력만 보고도 "환불까지 나간 취소인지"를 알 수 있다(환불이 없으면 둘 다 null).
     *
     * @param actorType     행위자 구분. 'USER'(본인 취소) 또는 'ADMIN'(관리자 대행 취소).
     *                      DB CHECK 제약이 'USER','ADMIN','SYSTEM','PAYMENT'만 허용한다.
     * @param isAdminAction 관리자 대행 취소면 true. actorType과 중복처럼 보이지만 조회 조건으로
     *                      쓰라고 V1 스키마부터 따로 있던 컬럼이라 함께 채운다.
     */
    int insertCanceledHistory(
            @Param("reservationId") Long reservationId,
            @Param("previousStatus") String previousStatus,
            @Param("reason") String reason,
            @Param("changedBy") Long changedBy,
            @Param("actorType") String actorType,
            @Param("isAdminAction") boolean isAdminAction,
            @Param("refundId") Long refundId,
            @Param("refundAmount") Long refundAmount,
            @Param("now") LocalDateTime now
    );
}
