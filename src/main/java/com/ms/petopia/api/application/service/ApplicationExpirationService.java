package com.ms.petopia.api.application.service;

import com.ms.petopia.api.application.domain.BoothSlotHallRef;
import com.ms.petopia.api.application.mapper.ApplicationExpirationMapper;
import com.ms.petopia.api.application.mapper.ApplicationMapper;
import com.ms.petopia.api.fair.service.BoothSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationExpirationService {

    private final ApplicationExpirationMapper applicationExpirationMapper;
    private final ApplicationMapper applicationMapper;
    private final BoothSlotService boothSlotService;

    /*
     * 결제 기한이 지난 PAYMENT_PENDING 신청서를 찾아 CANCELED로 자동 전환한다.
     * CANCELED로 바뀌면 application.active_key 계산 컬럼 덕분에 우리 도메인 자체의
     * 가상 잠금(application.status 기반 EXISTS 판정)은 자동으로 풀리지만, fair 도메인의
     * booth_slots.locked_at은 별개라 명시적으로 해제 호출을 해줘야 한다.
     */
    @Transactional
    public int expireDueApplications(int batchSize) {

        if(batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }

        LocalDateTime now = LocalDateTime.now();

        // FOR UPDATE SKIP LOCKED로 조회 시점에 대상 행을 미리 잠가둔다
        List<Long> dueApplicationIds = applicationExpirationMapper.selectDueApplicationsForUpdate(now, batchSize);

        int expired = 0;

        for(Long applicationId : dueApplicationIds) {

            // 락 순서를 approveCancelRequest와 통일(취소요청 행 먼저)해서 교착상태 방지
            applicationMapper.lockPendingCancelRequestIfExists(applicationId);

            // 조건부 UPDATE라 그 사이 상태가 바뀌었으면(예: 결제 완료) 0행 반영되고 조용히 건너뜀
            int updated = applicationExpirationMapper.expirePaymentPendingApplication(applicationId, now);

            if(updated == 1) {

                expired++;
                // 딸려있던 처리 대기 중인 취소 요청이 있으면 함께 종료 처리 (없으면 0행, 정상)
                applicationMapper.closeRequestedCancelRequestByApplicationId(applicationId, now);

                // 슬롯 잠금 풀기 (fair 도메인 booth_slots.locked_at)
                for (BoothSlotHallRef ref : applicationMapper.selectSlotHallRefsByApplicationId(applicationId)) {
                    boothSlotService.unlockBoothSlot(ref.getHallId(), ref.getBoothSlotId());
                }

            }

        }

        return expired;

    }

}
