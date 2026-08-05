package com.ms.petopia.api.business.service;

import com.ms.petopia.api.auth.service.UserRoleService;
import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.dto.request.BusinessRegisterRequest;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/*
 * 사업자 저장 전용 컴포넌트.
 * 국세청 API 호출(외부 I/O)과 DB 저장(트랜잭션)을 분리하기 위해,
 * BusinessService와 별도 컴포넌트로 둔다.
 * (같은 클래스 안에서 @Transactional 메서드를 호출하면 프록시를 안 거쳐서
 *  트랜잭션이 적용되지 않기 때문에, 반드시 다른 빈으로 분리해야 한다.)
 */
@Component
@RequiredArgsConstructor
public class BusinessRegistrar {

    private final BusinessMapper businessMapper;
    private final UserRoleService userRoleService;

    @Transactional
    public Business save(Long ownerId, BusinessRegisterRequest request, Business.VerifyStatus verifyStatus) {

        // 동시 등록 방지: ownerId 단위 애플리케이션 락 (users 테이블에는 영향 없음)
        Integer locked = businessMapper.acquireRegistrationLock(ownerId);

        if (locked == null || locked != 1) {
            throw new CommonException(ErrorCode.BUSINESS_REGISTER_LOCK_TIMEOUT);
        }

        try {

            // insert 전에 먼저 확인 — 이번이 첫 사업자 등록인지
            boolean isFirstBusiness = businessMapper.selectByOwnerId(ownerId).isEmpty();

            // 검증 결과까지 확정된 상태로 한 번에 저장
            Business business = Business.builder()
                    .ownerId(ownerId)
                    .name(request.getName())
                    .ceoName(request.getCeoName())
                    .bizRegNo(request.getBizRegNo())
                    .startDate(request.getStartDate())
                    .address(request.getAddress())
                    .phone(request.getPhone())
                    .website(request.getWebsite())
                    .verifyStatus(verifyStatus)
                    .build();

            try {
                businessMapper.insertBusiness(business);
            } catch (DuplicateKeyException e) {
                throw new CommonException(ErrorCode.BUSINESS_DUPLICATE);
            }

            /*
             * 첫 사업자 등록일 때만 role 전환 (두 번째부턴 이미 VENDOR라 건드릴 필요 없음)
             * 사업자 저장과 role 전환을 같은 트랜잭션으로 묶는다 (하나 실패하면 둘 다 롤백)
             */
            if (isFirstBusiness) {
                userRoleService.grantVendorRole(ownerId);
            }

            // 재조회(정확한 값으로 응답 만들기 위해) 값 반환
            return businessMapper.selectById(business.getBusinessId());

        } finally {
            // 무조건 해제
            businessMapper.releaseRegistrationLock(ownerId);
        }

    }

}
