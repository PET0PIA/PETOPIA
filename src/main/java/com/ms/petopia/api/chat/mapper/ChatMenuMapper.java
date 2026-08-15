package com.ms.petopia.api.chat.mapper;

import com.ms.petopia.api.chat.entity.ChatMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMenuMapper {

    /** 위젯에 노출할 활성 메뉴를 순서대로 조회한다. */
    List<ChatMenu> selectActiveMenus();

    /** 비활성 메뉴는 찾지 않는다 - 내려간 버튼의 code로 대화를 시작할 수 없어야 한다. */
    ChatMenu selectActiveByCode(@Param("code") String code);

    /**
     * 이미 진행 중인 대화가 참조하는 메뉴. 활성 여부를 따지지 않는다 - 상담 도중 운영자가
     * 버튼을 내렸다고 그 대화의 AI 설정까지 사라지면 안 된다.
     */
    ChatMenu selectById(@Param("menuId") Long menuId);

    /** 관리자 화면용. 비활성 버튼까지 포함한다. */
    List<ChatMenu> selectAllMenus();

    void insert(ChatMenu menu);

    /** 현재 가장 큰 노출 순서. 새 버튼을 맨 뒤에 붙이려고 쓴다. 버튼이 없으면 0. */
    int selectMaxDisplayOrder();

    int update(ChatMenu menu);

    /**
     * 버튼을 내린다. 행을 지우지 않는 이유는 {@code chat_conversation.menu_id}와
     * {@code chat_message.menu_id}가 이 행을 참조하기 때문이다 - 지우면 과거 상담이
     * 어떤 문의로 들어온 것인지 영영 알 수 없게 된다.
     */
    int deactivate(@Param("menuId") Long menuId);

    int updateDisplayOrder(@Param("menuId") Long menuId, @Param("displayOrder") int displayOrder);
}
