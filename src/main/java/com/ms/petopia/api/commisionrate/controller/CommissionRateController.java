package com.ms.petopia.api.commisionrate.controller;

import com.ms.petopia.api.commisionrate.dto.CommissionRateResponse;
import com.ms.petopia.api.commisionrate.dto.UpdateCommissionRateRequest;
import com.ms.petopia.api.commisionrate.service.CommissionRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommissionRateController {

    private final CommissionRateService commissionRateService;

    // 현재 적용 요율 조회. fairId 없으면 전역값만, 있으면 행사별 override -> 전역값 순으로 해석.
    @GetMapping("/settlements/commission-rate")
    public CommissionRateResponse getRate(@RequestParam(required = false) Long fairId) {
        return commissionRateService.getEffectiveRate(fairId);
    }

    // 요율 설정(SUPER_ADMIN). role 검증은 settlement confirm/recalculate와 같은 이유로
    // 인증 도메인 완성 후 추가 예정(TODO) - 그 전까진 헤더로 받은 userId를 감사용으로만 기록한다.
    @PutMapping("/settlements/commission-rate")
    public CommissionRateResponse setRate(
            @RequestBody @Valid UpdateCommissionRateRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        return commissionRateService.setRate(request.scope(), request.fairId(), request.rate(), userId);
    }
}
