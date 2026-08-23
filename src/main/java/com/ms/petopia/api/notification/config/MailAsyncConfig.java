package com.ms.petopia.api.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 이메일 발송 전용 스레드 풀.
 *
 * <p>메일 발송은 SMTP 왕복이 걸리는 외부 I/O다. 커밋 후({@code afterCommit}) 보내더라도 그
 * 콜백은 여전히 요청 스레드에서 돌기 때문에, 동기로 두면 SMTP가 느려진 만큼 API 응답이 그대로
 * 늦어진다. 예약 확정처럼 사용자가 결과를 기다리는 경로에서는 메일이 늦게 가는 것보다 응답이
 * 늦는 편이 훨씬 나쁘다.
 *
 * <p><b>{@code chatAiExecutor}와 분리한 이유</b>: 두 작업 모두 수 초짜리 외부 I/O라 같은 풀에
 * 두면 서로를 밀어낸다. SMTP가 막혀 풀이 점유되면 AI 답변이 함께 멈추고, 그 반대도 마찬가지다.
 *
 * <p><b>큐를 500으로 넉넉히</b> 잡는다. 메일은 채팅 답변과 달리 늦게 도착해도 쓸모가 있어서
 * (예약확정 메일은 QR을 담고 있으니 몇 분 늦어도 의미가 있다) 버리는 것보다 밀리는 게 낫다.
 *
 * <p><b>거부 정책을 두지 않는다.</b> 기본값인 {@code AbortPolicy}가 그대로 예외를 던지고,
 * 제출한 쪽이 그것을 잡아 로그를 남긴다. {@code CallerRunsPolicy}로 바꾸면 큐가 찬 순간
 * 요청 스레드가 SMTP를 직접 태우게 되는데, 그건 이 풀을 만든 이유를 정확히 되돌리는 일이다.
 * 조용히 버리는 {@code DiscardPolicy}도 안 된다 — 메일이 사라진 사실이 어디에도 남지 않는다.
 *
 * <p>종료 시에는 큐를 비우고 나간다. 배포 중 재기동으로 발송 대기 중인 메일이 사라지면
 * 사용자는 QR이 담긴 예약확정 메일을 영영 받지 못한다(재시도 장치가 없다).
 */
@Configuration
public class MailAsyncConfig {

    public static final String MAIL_EXECUTOR = "mailExecutor";

    @Bean(MAIL_EXECUTOR)
    public ThreadPoolTaskExecutor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
