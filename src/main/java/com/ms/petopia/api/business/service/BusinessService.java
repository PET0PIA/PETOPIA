package com.ms.petopia.api.business.service;

import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.dto.request.BusinessRegisterRequest;
import com.ms.petopia.api.business.dto.response.BusinessResponse;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessMapper businessMapper;
    private final NtsBusinessVerificationClient ntsClient;

    // 사업자 등록(국세청 진위확인 포함)
    @Transactional
    public BusinessResponse registerBusiness(Long ownerId, BusinessRegisterRequest request) {

        // 국세청 진위 확인 API 호출(동기)
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

        // 여기 도달하면 항상 VERIFIED, 검증 결과까지 확정된 상태로 한 번에 저장
        Business business = Business.builder()
                .ownerId(ownerId)
                .name(request.getName())
                .ceoName(request.getCeoName())
                .bizRegNo(request.getBizRegNo())
                .startDate(request.getStartDate())
                .address(request.getAddress())
                .phone(request.getPhone())
                .website(request.getWebsite())
                .verifyStatus(Business.VerifyStatus.VERIFIED)
                .build();

        // 검증 결과까지 포함해서 한 번에 저장
        businessMapper.insertBusiness(business);

        // 재조회 (정확한 값으로 응답 만들기 위해)
        Business saved = businessMapper.selectById(business.getBusinessId());

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
