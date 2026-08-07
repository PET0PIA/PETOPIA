package com.ms.petopia.api.application.service;

import com.ms.petopia.api.application.mapper.ApplicationExpirationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationExpirationService {

    private final ApplicationExpirationMapper applicationExpirationMapper;

    /*
     * 결제 기한이 지난 PAYMENT_PENDING 신청서를 찾아 CANCELED로 자동 전환한다.
     * CANCELED로 바뀌면 application.active_key 계산 컬럼 덕분에 부스 슬롯도
     * 자동으로 풀리고(반려/취소 승인 때와 동일 메커니즘), 재신청도 바로 가능해진다
     * - 별도 슬롯 해제 코드가 필요 없다.
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

            // 조건부 UPDATE라 그 사이 상태가 바뀌었으면(예: 결제 완료) 0행 반영되고 조용히 건너뜀
            int updated = applicationExpirationMapper.expirePaymentPendingApplication(applicationId, now);

            if(updated == 1) {
                expired++;
            }

        }

        return expired;

    }

}
