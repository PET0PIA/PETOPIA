package com.ms.petopia.api.application.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ApplicationExpirationMapper {

    // 결제 기한(payment_due_at)이 지났는데 아직 결제 안 한(PAYMENT_PENDING) 신청서 ID 목록 조회
    List<Long> selectDueApplicationsForUpdate(@Param("now") LocalDateTime now,
                                              @Param("limit") int limit);

    // 자동 취소 처리 (반환: 영향받은 행 수. 0이면 이미 다른 스케줄러 인스턴스가 먼저 처리한 것)
    int expirePaymentPendingApplication(@Param("applicationId") Long applicationId,
                                        @Param("now") LocalDateTime now);

}
