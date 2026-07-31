package com.ms.petopia.api.business.service;

import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.dto.response.BusinessResponse;
import com.ms.petopia.api.business.mapper.BusinessMapper;
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

}
