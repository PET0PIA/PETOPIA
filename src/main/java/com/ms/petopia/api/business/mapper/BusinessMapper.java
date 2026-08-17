package com.ms.petopia.api.business.mapper;

import com.ms.petopia.api.business.domain.Business;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BusinessMapper {

    // 사업자 등록. insert 후 business.businessId에 생성된 PK가 채워짐(useGeneratedKeys)
    void insertBusiness(Business business);

    // 내 사업자 목록 조회
    List<Business> selectByOwnerId(@Param("ownerId") Long ownerId);

    // 사업자 상세 조회
    Business selectById(@Param("businessId") Long businessId);

    // 사업자 등록 진위 확인
    void updateVerifyStatus(@Param("businessId") Long businessId,
                            @Param("verifyStatus") String verifyStatus);

    // 동시 등록 직렬화용 락 획득 (반환: 1=성공, 0=타임아웃, null=에러)
    Integer acquireRegistrationLock(@Param("ownerId") Long ownerId);

    // 락 해제 (반환: 1=성공, 0=락을 안 갖고 있었음, null=락이 존재하지 않음)
    Integer releaseRegistrationLock(@Param("ownerId") Long ownerId);

    // 같은 사업자의 동시 신청 직렬화용 락 (application 도메인에서 호출)
    Long lockBusinessForApplication(@Param("businessId") Long businessId);

    // 심사 상태별 사업자 목록 조회 (관리자용)
    List<Business> selectByApprovalStatus(@Param("approvalStatus") String approvalStatus);

    // 사업자 상세 조회 (관리자용, 소유자 필터 없음)
    Business selectByIdForReview(@Param("businessId") Long businessId);

    // 사업자 승인. PENDING_REVIEW일 때만 반영되는 조건부 UPDATE (반환된 행 수로 동시 처리 감지)
    int updateApprovalApproved(@Param("businessId") Long businessId,
                               @Param("reviewerId") Long reviewerId,
                               @Param("reviewedAt") LocalDateTime reviewedAt);

    // 이 소유자가 이미 승인된 사업자를 하나라도 갖고 있는지 (VENDOR 재부여 방지용)
    boolean existsApprovedBusinessForOwner(@Param("ownerId") Long ownerId);

}
