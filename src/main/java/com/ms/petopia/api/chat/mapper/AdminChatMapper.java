package com.ms.petopia.api.chat.mapper;

import com.ms.petopia.api.chat.dto.AdminChatConversationRow;
import com.ms.petopia.api.chat.dto.AdminChatFilter;
import com.ms.petopia.api.chat.dto.ChatMenuStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminChatMapper {

    List<AdminChatConversationRow> selectConversations(@Param("filter") AdminChatFilter filter,
                                                       @Param("offset") long offset,
                                                       @Param("limit") int limit);

    long countConversations(@Param("filter") AdminChatFilter filter);

    /** 답변 대기 건수. 콘솔 배지에 쓴다. */
    long countWaiting();

    /** 대화가 속한 문의 유형 라벨. 상세 화면 머리말에 쓴다. */
    String selectMenuLabel(@Param("conversationId") Long conversationId);

    /** 문의 유형별 지표. {@code since} 이후 생성된 대화만 센다. */
    List<ChatMenuStat> selectMenuStats(@Param("since") LocalDateTime since);
}
