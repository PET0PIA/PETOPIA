package com.ms.petopia.api.business.controller;

import com.ms.petopia.api.business.dto.response.BusinessResponse;
import com.ms.petopia.api.business.service.BusinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;

    // 내 사업자 목록 조회
    @GetMapping
    public List<BusinessResponse> getMyBusinesses() {

        // TODO: 로그인 붙으면 인증 정보에서 추출
        Long ownerId = 1L;

        return businessService.getMyBusinesses(ownerId);

    }

    // 사업자 상세 조회 (진위확인 상태 포함)
    @GetMapping("/{businessId}")
    public BusinessResponse getBusiness(@PathVariable Long businessId) {

        // TODO: 로그인 붙으면 인증 정보에서 추출
        Long ownerId = 1L;

        return businessService.getBusiness(ownerId, businessId);
    }

}
