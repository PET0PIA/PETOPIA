package com.ms.petopia.api.application.mapper;

import com.ms.petopia.api.application.domain.Application;
import com.ms.petopia.api.application.domain.ApplicationForm;
import com.ms.petopia.api.application.domain.ApplicationSlot;
import com.ms.petopia.api.application.dto.response.ApplicationDetailResponse;
import com.ms.petopia.api.application.dto.response.ApplicationSlotDetailResponse;
import com.ms.petopia.api.application.dto.response.ApplicationSummaryResponse;
import com.ms.petopia.api.application.dto.response.BoothSlotLockStatusResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    // 1단계: application_slot을 booth_slot_id 기준으로 잠금 (JOIN 없는 단일 테이블 조회라 갭 락이 확실히 걸림)
    void lockApplicationSlotsByBoothSlotIds(@Param("boothSlotIds") List<Long> boothSlotIds);

    // 2단계: 잠금 확보 후, 실제로 활성 신청에 걸린 슬롯만 조회 (1단계에서 이미 잠겼으니 FOR UPDATE 불필요)
    List<Long> selectLockedBoothSlotIds(@Param("boothSlotIds") List<Long> boothSlotIds);

    // 내 신청 현황 목록 조회 (businessId는 선택적 필터)
    List<ApplicationSummaryResponse> selectMyApplications(@Param("ownerId") Long ownerId,
                                                          @Param("businessId") Long businessId);

    // 신청 상세 조회 (application + application_form 조인, slots는 별도 조회)
    ApplicationDetailResponse selectApplicationDetail(@Param("applicationId") Long applicationId);

    // 신청 상세의 선택 슬롯 목록 조회
    List<ApplicationSlotDetailResponse> selectApplicationSlotDetails(@Param("applicationId") Long applicationId);

}
