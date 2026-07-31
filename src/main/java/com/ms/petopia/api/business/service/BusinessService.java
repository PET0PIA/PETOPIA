package com.ms.petopia.api.business.service;

import com.ms.petopia.api.business.domain.Business;
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

    // 내 사업자 목록 조회
    public List<BusinessResponse> getMyBusinesses(Long ownerId) {

        List<Business> businesses = businessMapper.selectByOwnerId(ownerId);

        return businesses.stream()
                .map(BusinessResponse::from)
                .toList();

    }

    // 사업자 상세 조회 (진위확인 상태 포함)
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
