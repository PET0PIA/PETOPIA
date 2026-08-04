package com.ms.petopia.api.business.service;

import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.dto.request.BusinessRegisterRequest;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public Business save(Long ownerId, BusinessRegisterRequest request, Business.VerifyStatus verifyStatus) {

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

        // 검증 결과까지 포함해서 한 번에 저장
        businessMapper.insertBusiness(business);

        // 재조회(정확한 값으로 응답 만들기 위해) 값 반환
        return businessMapper.selectById(business.getBusinessId());

    }

}
