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
     * AI가 답한 뒤 입력을 잠근다.
     *
     * @return 1이면 잠금 성공. 0이면 그 사이 상담사가 답했거나 대화가 끝난 것이라
     *         잠그지 않는 편이 맞다.
     */
    int markAiAnswered(@Param("conversationId") Long conversationId,
                       @Param("now") LocalDateTime now);

    /**
     * AI 답변 슬롯을 선점한다.
     *
     * <p>호출 <b>전에</b> 카운트를 올려야 한다. 호출한 뒤에 올리면, 동시에 들어온 두 요청이
     * 모두 0을 보고 Claude를 두 번 부른다(비용은 두 배, 답변은 중복).
     *
     * @param limit 대화당 허용 횟수
     * @return 1이면 선점 성공
     */
    int claimAiAnswer(@Param("conversationId") Long conversationId,
                      @Param("limit") int limit);

    /** 선점했지만 답변하지 못했을 때 되돌린다(대기열 정원 반납과 같은 패턴). */
    int releaseAiAnswer(@Param("conversationId") Long conversationId);

    /** 선점 직후 몇 번째 답변인지 알아내려고 읽는다(마지막 답변이면 종료 안내를 붙여야 한다). */
    Integer selectAiAnswerCount(@Param("conversationId") Long conversationId);

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
