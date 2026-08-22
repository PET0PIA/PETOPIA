package com.ms.petopia.api.chat.mapper;

import com.ms.petopia.api.chat.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ChatConversationMapper {

    void insert(ChatConversation conversation);

    ChatConversation selectById(@Param("conversationId") Long conversationId);

    /**
     * 위젯 재진입 시 이어볼 대화. 가장 최근 것 하나만 본다.
     *
     * <p>종료된 대화도 포함한다 - 종료했다고 화면에서 내용까지 사라지면 방금 받은 답변조차
     * 다시 볼 수 없다. 종료 여부는 status로 내려가고, 화면이 읽기 전용으로 처리한다.
     *
     * <p>로그인 사용자면 user_id로, 게스트면 guest_key로 찾는다. 둘 다 없으면 조회 자체를
     * 하지 않는다(서비스에서 거른다).
     */
    ChatConversation selectLatest(@Param("userId") Long userId,
                                  @Param("guestKey") String guestKey);

    /**
     * 사용자 메시지를 받을 수 있는 상태일 때만 WAITING_AGENT로 넘긴다.
     *
     * <p>이 UPDATE의 반영 행 수가 곧 "보낼 수 있는가"의 판정이다. 조회로 상태를 확인한 뒤
     * 따로 UPDATE하면 그 사이에 다른 요청이 끼어들 수 있다 - 확인과 전이를 한 문장으로 묶는다.
     *
     * @return 1이면 전이 성공, 0이면 이미 대기 중이거나 종료된 대화
     */
    int markWaitingAgent(@Param("conversationId") Long conversationId,
                         @Param("now") LocalDateTime now);

    /**
     * 대화 행을 잠근다(값은 쓰지 않는다).
     *
     * <p>같은 대화에 AI 답변 작업이 둘 동시에 돌 수 있다 - {@link #claimAiCall}의 stale 창이
     * 지나면 뒤이은 질문이 다시 선점할 수 있고, 그때 두 작업이 겹친다. 그래서 "이미 붙었는지
     * 읽어보고 없으면 붙인다" 식의 판정은 둘이 같은 스냅샷을 보고 둘 다 붙이는 창이 있다.
     *
     * <p>읽기 전에 이 잠금을 잡으면 뒤에 온 쪽이 앞의 커밋을 기다린 뒤 판정하므로 그 창이
     * 닫힌다. 잠금 구간은 Claude 호출 <b>이후</b>의 쓰기 몇 줄뿐이고, 잠그는 순서도
     * 다른 경로와 같아(대화 → 메시지) 순서 역전이 없다.
     */
    void lockById(@Param("conversationId") Long conversationId);

    /**
     * AI가 응대를 마쳤다고 표시한다.
     *
     * <p>이름이 {@code markAiAnswered}였을 때와 달리 <b>잠그지 않는다.</b> {@code AI_HANDLED}는
     * 입력이 열린 상태이고, 바뀌는 것은 상담사 대기열에 뜨는지 여부뿐이다.
     *
     * <p><b>이 전이가 AI 답변의 저장 권한이다.</b> 호출자는 1을 받은 뒤에만 말풍선을 붙이고
     * 지표를 올린다 - 반대 순서였을 때는 전이가 실패해도 답변이 남아, 상담사가 이어받은
     * 대화나 끝난 대화에 자동 답변이 뒤늦게 끼어들었다.
     *
     * @return 1이면 전이 성공. 0이면 그 사이 상담사가 답했거나 대화가 끝난 것이라
     *         되돌리지 않는 편이 맞고, 그 답변도 저장하지 않는다.
     */
    int markAiHandled(@Param("conversationId") Long conversationId,
                      @Param("now") LocalDateTime now);

    /**
     * 진행 중인 AI 호출을 선점한다.
     *
     * <p><b>횟수 한도가 아니라 중복 제거다.</b> 답을 기다리다 같은 질문을 연달아 보내면
     * 호출이 동시에 여러 건 돌고, 답변이 순서 없이 한 창에 쌓인다. 앞 답변이 도착해 반납되면
     * 다음 질문은 그대로 답을 받는다.
     *
     * <p>호출 <b>전에</b> 선점해야 한다. 호출한 뒤에 표시하면 동시에 들어온 두 요청이 모두
     * 빈 값을 보고 Claude를 두 번 부른다.
     *
     * <p>상담사가 배정된 대화는 선점되지 않는다. 서비스도 같은 검사를 하지만 그쪽은
     * 트랜잭션 시작 시점의 행을 보므로, 그 사이 배정이 일어나면 통과한다 - 배타를 실제로
     * 집행하는 것은 이 UPDATE의 조건이다.
     *
     * @param staleBefore 이 시각보다 오래된 선점은 없는 것으로 본다. 프로세스가 죽어 반납되지
     *                    않은 행을 스스로 풀어주기 위한 값이다.
     * @return 1이면 선점 성공
     */
    int claimAiCall(@Param("conversationId") Long conversationId,
                    @Param("now") LocalDateTime now,
                    @Param("staleBefore") LocalDateTime staleBefore);

    /** 선점을 반납한다. 성공·이관·예외·큐 거부 네 경로 모두에서 불린다. */
    int releaseAiCall(@Param("conversationId") Long conversationId);

    /** AI가 응대한 횟수. 한도가 아니라 지표용이라 조건 없이 올린다. */
    int incrementAiAnswerCount(@Param("conversationId") Long conversationId);

    /** 상담사가 답했을 때. 잠금이 풀린다. */
    int markInProgress(@Param("conversationId") Long conversationId,
                       @Param("assignedAdminId") Long assignedAdminId,
                       @Param("now") LocalDateTime now);

    /** 이미 종료된 대화를 다시 종료하지 않도록 조건을 건다. */
    int markClosed(@Param("conversationId") Long conversationId,
                   @Param("now") LocalDateTime now);

    /** 마지막 메시지 시각만 갱신한다(상태 전이가 없는 고정 답변 등). */
    void touchLastMessageAt(@Param("conversationId") Long conversationId,
                            @Param("now") LocalDateTime now);

    /**
     * 게스트로 시작한 대화를 로그인 사용자에게 승계한다.
     *
     * <p>이미 user_id가 있는 대화는 건드리지 않는다 - 남의 대화를 자기 것으로 만들 수 있는
     * 경로가 되면 안 된다.
     */
    int claimByUser(@Param("conversationId") Long conversationId,
                    @Param("userId") Long userId,
                    @Param("guestKey") String guestKey);
}
