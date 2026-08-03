package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.OnsiteSalesPolicyResponse;
import com.ms.petopia.api.reservation.dto.UpdateOnsiteSalesPolicyRequest;
import com.ms.petopia.api.reservation.service.OnsiteSalesPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/fairs/{fairId}/dates/{fairDateId}/onsite-sales-policy")
@RequiredArgsConstructor
public class OnsiteSalesAdminController {

    private final OnsiteSalesPolicyService policyService;

    @PutMapping
    public OnsiteSalesPolicyResponse savePolicy(
            @PathVariable Long fairId,
            @PathVariable Long fairDateId,
            @RequestHeader(TemporaryAuthHeaders.USER_ID) Long actorUserId,
            @RequestBody UpdateOnsiteSalesPolicyRequest request
    ) {
        // TODO 인증 도메인 완성 후 actorUserId를 인증 Principal에서 가져온다.
        return policyService.save(fairId, fairDateId, actorUserId, request);
    }
}
