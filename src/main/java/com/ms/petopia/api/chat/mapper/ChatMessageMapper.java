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
     * 같은 내용의 SYSTEM 메시지가 이 대화에 이미 있는지.
     *
     * <p>운영 안내를 두 번 붙이지 않기 위한 확인이다. 내용으로 비교하는 이유는 안내 문구가
     * 운영자가 고치는 설정값이라, 코드가 붙일 수 있는 식별자가 따로 없기 때문이다.
     * 문구를 고치면 그 뒤로는 새 문구 기준으로 판정한다 - 옛 안내가 남아 있어도 한 번은
     * 새 문구가 붙는데, 문구가 바뀐 시점이니 그게 맞다.
     */
    boolean existsSystemMessage(@Param("conversationId") Long conversationId,
                                @Param("content") String content);

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
