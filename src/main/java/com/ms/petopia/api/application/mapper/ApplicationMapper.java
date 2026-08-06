package com.ms.petopia.api.application.mapper;

import com.ms.petopia.api.application.domain.Application;
import com.ms.petopia.api.application.domain.ApplicationForm;
import com.ms.petopia.api.application.domain.ApplicationSlot;
import com.ms.petopia.api.application.dto.response.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ApplicationMapper {

    // 특정 행사의 활성 부스 슬롯 전체 + 잠금 상태 조회
    List<BoothSlotLockStatusResponse> selectBoothSlotsWithLockStatus(@Param("fairId") Long fairId);

    // 행사 존재 확인
    boolean existsFair(@Param("fairId") Long fairId);

    // application 저장 (PENDING_REVIEW로 생성)
    void insertApplication(Application application);

    // application_form 저장 (application과 1:1)
    void insertApplicationForm(ApplicationForm applicationForm);

    // application_slot 저장 (선택한 슬롯 개수만큼 반복 호출)
    void insertApplicationSlot(ApplicationSlot applicationSlot);

    // 같은 사업자·같은 행사에 활성 신청(PENDING_REVIEW/PAYMENT_PENDING/CONFIRMED)이 있는지 확인
    boolean existsActiveApplication(@Param("businessId") Long businessId, @Param("fairId") Long fairId);

    // 재조회(submitted_at 등 DB 기본값 반영해서 정확한 응답 만들기 위해)
    Application selectById(@Param("applicationId") Long applicationId);

    // 잠금 확보 후, 실제로 활성 신청에 걸린 슬롯만 조회 (1단계에서 이미 잠겼으니 FOR UPDATE 불필요)
    List<Long> selectLockedBoothSlotIds(@Param("boothSlotIds") List<Long> boothSlotIds);

    // 내 신청 현황 목록 조회 (businessId는 선택적 필터)
    List<ApplicationSummaryResponse> selectMyApplications(@Param("ownerId") Long ownerId,
                                                          @Param("businessId") Long businessId);

    // 신청 상세 조회 (application + application_form 조인, slots는 별도 조회)
    ApplicationDetailResponse selectApplicationDetail(@Param("applicationId") Long applicationId);

    // 신청 상세의 선택 슬롯 목록 조회
    List<ApplicationSlotDetailResponse> selectApplicationSlotDetails(@Param("applicationId") Long applicationId);

    // 부스 슬롯별 동시 신청 직렬화용 락 획득 (반환: 1=성공, 0=타임아웃, null=에러)
    Integer acquireBoothSlotLock(@Param("boothSlotId") Long boothSlotId);

    // 락 해제
    Integer releaseBoothSlotLock(@Param("boothSlotId") Long boothSlotId);

    // 담당 행사에 들어온 신청 목록 조회 (status는 선택적 필터)
    List<ApplicationReviewSummaryResponse> selectApplicationsByFair(@Param("fairId") Long fairId,
                                                                    @Param("status") String status);

    // 신청이 선택한 슬롯들의 가격 합계 (승인 시 final_price 자동 산출용)
    Long sumSlotPricesByApplicationId(@Param("applicationId") Long applicationId);

    // 승인 처리 (반환: 영향받은 행 수. 0이면 이미 다른 요청이 먼저 처리한 것)
    int updateApplicationApproved(@Param("applicationId") Long applicationId,
                                  @Param("finalPrice") Long finalPrice,
                                  @Param("paymentDueAt") LocalDateTime paymentDueAt,
                                  @Param("reviewedAt") LocalDateTime reviewedAt);

    // 반려 처리 (반환: 영향받은 행 수. 0이면 이미 다른 요청이 먼저 처리한 것)
    int updateApplicationRejected(@Param("applicationId") Long applicationId,
                                  @Param("rejectReason") String rejectReason,
                                  @Param("reviewedAt") LocalDateTime reviewedAt);

    // 담당 행사에 들어온 취소 요청 목록 조회 (status는 선택적 필터)
    List<ApplicationCancelRequestSummaryResponse> selectCancelRequestsByFair(@Param("fairId") Long fairId,
                                                                             @Param("status") String status);

}
