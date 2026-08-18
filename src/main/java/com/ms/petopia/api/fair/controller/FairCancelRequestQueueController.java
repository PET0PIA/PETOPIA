package com.ms.petopia.api.fair.controller;

import com.ms.petopia.api.fair.dto.FairCancelRequestQueueItemResponse;
import com.ms.petopia.api.fair.dto.FairCancelRequestStatus;
import com.ms.petopia.api.fair.service.FairCancelRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 특정 행사에 갇히지 않고 전체 행사를 가로질러 취소 신청을 훑어보는 SUPER_ADMIN 전용 큐.
 * {@link FairCancelRequestController}({@code /api/fairs/{fairId}/fair-cancel-requests})는
 * 이미 담당 행사를 아는 상태에서 그 행사 이력을 보는 화면 전용이라 fairId 없이는 호출할 수
 * 없다 - "지금 심사해야 할 취소 신청이 뭐가 있는지" 찾는 용도로는 별도 컨트롤러가 필요하다.
 */
@RestController
@RequestMapping("/api/fair-cancel-requests")
@RequiredArgsConstructor
public class FairCancelRequestQueueController {

    private final FairCancelRequestService cancelRequestService;

    @GetMapping
    public List<FairCancelRequestQueueItemResponse> getQueue(
            @RequestParam(required = false) FairCancelRequestStatus status
    ) {
        // SecurityConfig에서 SUPER_ADMIN role만 이 엔드포인트에 도달할 수 있게 막는다.
        // status를 생략하면 전체, 주면(예: PENDING) 그 상태만 걸러 심사 큐로 쓸 수 있다.
        return cancelRequestService.getQueue(status);
    }
}
