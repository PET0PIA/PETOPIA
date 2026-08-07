package com.ms.petopia.api.application.service;

import com.ms.petopia.api.application.mapper.ApplicationExpirationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/*
 * 취소된 행사(fairs.canceled_at IS NOT NULL)에 속한 참가 신청을 찾아 자동 취소 처리하는 배치.
 * 환불은 이 배치의 책임이 아니다 - fair 도메인의 FairCancelRefundJob이 취소된 행사의 COMPLETED
 * 결제(VENDOR_FEE 포함)를 스스로 찾아 이미 환불 처리한다. 이 배치는 application.status를
 * CANCELED로 전환하는 것만 담당한다.
 *
 * "신청서 하나 취소" 트랜잭션 로직은 ApplicationService.cancelApplicationForCanceledFair에 있고,
 * 이 클래스는 순수하게 "배치 조회 + 반복 호출 + 개별 실패 격리"만 담당한다.
 *
 * 배치 순회 로직과 트랜잭션 로직을 같은 클래스에 두지 않은 이유: 같은 클래스 안에서
 * this.xxx()로 호출하면 스프링 프록시를 안 거쳐서 @Transactional이 무시되기 때문
 * (자기 자신 호출 시 AOP 프록시가 우회되는 스프링의 잘 알려진 제약).
 * ApplicationService는 별도 빈이라 프록시가 정상 적용된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationFairCancellationJob {

    // 한 번 실행될 때 최대 몇 건까지 처리할지 - 너무 많으면 개별 트랜잭션이 연속으로 오래 걸린다
    private static final int BATCH_SIZE = 200;

    private final ApplicationExpirationMapper applicationExpirationMapper;
    private final ApplicationService applicationService;

    // 기본 60초 간격으로 실행
    @Scheduled(fixedDelayString = "${petopia.application.fair-cancellation-check-interval-ms:60000}")
    public void cancelApplicationsForCanceledFairs() {

        // 취소된 행사에 속한, 아직 활성 상태(PAYMENT_PENDING/CONFIRMED)인 신청서 후보 조회
        List<Long> candidateIds = applicationExpirationMapper.selectApplicationsForCanceledFairs(BATCH_SIZE);

        int canceled = 0;

        for(Long applicationId : candidateIds) {

            try {

                /*
                 * 신청서 하나당 독립적인 트랜잭션(취소 UPDATE)을 실행.
                 * false면 그 사이 다른 경로(예: 사업자 자진 취소)로 이미 처리된 것 -
                 * 정상 상황이라 예외 아님, 그냥 카운트만 안 함.
                 */
                if(applicationService.cancelApplicationForCanceledFair(applicationId)) {
                    canceled++;
                }

            } catch(Exception e) {

                /*
                 * 이 건 처리가 실패해도 나머지 배치는 계속 진행한다.
                 * 실패한 건은 다음 스케줄 실행 때 다시 후보로 조회돼 재시도된다(멱등 - 이미
                 * CANCELED로 바뀐 건은 조건부 UPDATE에서 0행 반영되며 자연스럽게 걸러짐).
                 */
                log.error("행사 취소로 인한 참가 신청 자동취소 처리 실패. applicationId={}", applicationId, e);

            }

        }

        if(canceled > 0) {
            log.info("취소된 행사에 속한 참가 신청을 자동 취소했습니다. count={}", canceled);
        }

    }

}
