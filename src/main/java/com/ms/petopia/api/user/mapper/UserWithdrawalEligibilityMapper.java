package com.ms.petopia.api.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

//탈퇴 가능 여부 확인용
@Mapper
public interface UserWithdrawalEligibilityMapper {

    //결제 대기 중이거나 확정된 예약이 있는지
    boolean existsActiveReservation(@Param("userId") Long userId);

    //승인 대기 중인 결제가 있는지
    boolean existsPendingPayment(@Param("userId") Long userId);
}
