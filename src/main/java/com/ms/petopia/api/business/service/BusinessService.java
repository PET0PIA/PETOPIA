package com.ms.petopia.api.business.service;

import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.dto.request.BusinessRegisterRequest;
import com.ms.petopia.api.business.dto.response.BusinessResponse;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessMapper businessMapper;
    private final NtsBusinessVerificationClient ntsClient;
    private final BusinessRegistrar businessRegistrar;

    // 사업자 등록(국세청 진위확인 포함)
    public BusinessResponse registerBusiness(Long ownerId, BusinessRegisterRequest request) {

        // 국세청 진위 확인 API 호출(동기) - 트랜잭션 없이, DB 커넥션 안 붙잡은 상태로 호출
        boolean valid;

        try {

            valid = ntsClient.validate(
                    request.getBizRegNo(),
                    request.getCeoName(),
                    request.getStartDate()
            );

        }catch (Exception e) {
            /*
             * API 호출 자체가 실패한 경우 - 아무것도 저장하지 않고 바로 에러 응답
             * (이러면 사용자가 같은 정보로 바로 재시도 가능, UK 충돌도 안 생김)
             */
            throw new CommonException(ErrorCode.NTS_API_UNAVAILABLE);
        }

        // 진위확인 실패(INVALID) - 등록 자체를 막음
        if (!valid) {
            throw new CommonException(ErrorCode.BUSINESS_VERIFICATION_FAILED);
        }

        // 여기 도달하면 항상 VERIFIED. 저장은 별도 컴포넌트(트랜잭션 안)에서 수행
        Business saved = businessRegistrar.save(ownerId, request, Business.VerifyStatus.VERIFIED);

        return BusinessResponse.from(saved);

    }

    // 내 사업자 목록 조회
    public List<BusinessResponse> getMyBusinesses(Long ownerId) {

        List<Business> businesses = businessMapper.selectByOwnerId(ownerId);

        return businesses.stream()
                .map(BusinessResponse::from)
                .toList();

    }

    // 사업자 상세 조회(진위확인 상태 포함)
    public BusinessResponse getBusiness(Long ownerId, Long businessId) {

        Business business = businessMapper.selectById(businessId);

        if(business == null) {
            throw new CommonException(ErrorCode.BUSINESS_NOT_FOUND);
        }

        if(!business.getOwnerId().equals(ownerId)) {
            throw new CommonException(ErrorCode.ACCESS_DENIED, "본인 소유의 사업자만 조회할 수 있습니다.");
        }

        return BusinessResponse.from(business);

    }

}
