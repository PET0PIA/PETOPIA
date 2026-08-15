package com.ms.petopia.api.chat.mapper;

import com.ms.petopia.api.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    void insert(ChatMessage message);

    /**
     * 커서 이후의 메시지를 오래된 순으로 가져온다.
     *
     * @param afterMessageId null이면 처음부터. 폴링은 마지막으로 받은 message_id를 넘긴다.
     */
    List<ChatMessage> selectByConversation(@Param("conversationId") Long conversationId,
                                           @Param("afterMessageId") Long afterMessageId,
                                           @Param("limit") int limit);

    /**
     * 최근 메시지를 오래된 순으로 돌려준다(AI 프롬프트용).
     *
     * <p>{@link #selectByConversation}은 앞에서부터 읽는 커서 조회라, 대화가 길어지면
     * 최근 문맥이 아니라 첫 부분을 가져온다. 여기서 필요한 건 그 반대다.
     */
    List<ChatMessage> selectRecentByConversation(@Param("conversationId") Long conversationId,
                                                 @Param("limit") int limit);

    /**
     * 이 요청자의 최근 메시지를 <b>상담을 가로질러</b> 오래된 순으로 돌려준다.
     *
     * <p>위젯 채팅창은 상담 단위로 끊기지 않는다. 새 문의를 시작해도 앞선 상담 내용이
     * 위에 그대로 남아 있어야 사용자가 "아까 뭐라고 답변받았더라"를 확인할 수 있다.
     */
    List<ChatMessage> selectRecentByRequester(@Param("userId") Long userId,
                                              @Param("guestKey") String guestKey,
                                              @Param("limit") int limit);
}
