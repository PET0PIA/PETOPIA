package com.ms.petopia.api.payment.mapper;

import com.ms.petopia.api.payment.dto.PaymentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * PAYMENT 테이블 조회용 매퍼.
 *
 * <p>{@code @MapperScan}을 두지 않았으므로 {@code @Mapper}가 붙은 인터페이스만 빈으로 등록된다.
 * XML은 {@code classpath:mapper/**}/*.xml 규칙에 따라 {@code mapper/payment/PaymentMapper.xml}에 있다.
 */
@Mapper
public interface PaymentMapper {

    /**
     * 결제 PK로 한 건 조회한다. 없으면 null을 반환한다(예외로 던지지 않음 — 존재 유무 판단은
     * 서비스 계층의 책임으로 남겨둔다).
     */
    PaymentRow selectById(@Param("paymentId") Long paymentId);
}
