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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    @Transactional
    public Business save(Long ownerId, BusinessRegisterRequest request,
                         Business.VerifyStatus verifyStatus, String businessRegDocKey) {

        // 동시 등록 방지: ownerId 단위 애플리케이션 락 (users 테이블에는 영향 없음)
        Integer locked = businessMapper.acquireRegistrationLock(ownerId);

        if (locked == null || locked != 1) {
            throw new CommonException(ErrorCode.BUSINESS_REGISTER_LOCK_TIMEOUT);
        }

        /*
         * 락 해제는 메서드 종료 시점이 아니라, 트랜잭션이 실제로 커밋/롤백된 직후에 해야 한다.
         * @Transactional의 실제 커밋은 이 메서드가 return한 뒤 프록시에서 일어나므로,
         * finally에서 바로 풀면 커밋 전에 락이 풀려 다른 트랜잭션이 아직 안 보이는(uncommitted)
         * insert 이전 상태를 읽고 잘못 판정할 수 있다.
         */
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCompletion(int status) {
                businessMapper.releaseRegistrationLock(ownerId);
            }

        });

        /*
         * 관리자 승인 전이라 VENDOR 권한은 아직 부여하지 않는다(승인 시점에 BusinessService에서 부여).
         * approvalStatus는 항상 PENDING_REVIEW로 고정 - 이 메서드로는 그 외 상태를 만들 수 없다.
         */
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
                .businessRegDocKey(businessRegDocKey)
                .approvalStatus(Business.ApprovalStatus.PENDING_REVIEW)
                .build();

        try {
            businessMapper.insertBusiness(business);
        } catch (DuplicateKeyException e) {
            throw new CommonException(ErrorCode.BUSINESS_DUPLICATE);
        }

        // 재조회(정확한 값으로 응답 만들기 위해) 값 반환
        return businessMapper.selectById(business.getBusinessId());

    }

}
