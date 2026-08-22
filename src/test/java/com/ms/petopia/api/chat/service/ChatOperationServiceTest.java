package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.dto.AdminChatMenuRequest;
import com.ms.petopia.api.chat.dto.ChatAnswerType;
import com.ms.petopia.api.chat.entity.ChatMenu;
import com.ms.petopia.api.chat.mapper.AdminChatMapper;
import com.ms.petopia.api.chat.mapper.ChatBusinessHourMapper;
import com.ms.petopia.api.chat.mapper.ChatMenuMapper;
import com.ms.petopia.api.chat.mapper.ChatSettingMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/*
 * 운영 설정 서비스 테스트. 지금 지키는 규칙은 하나다 -
 * 고정 답변 유형은 답변 본문 없이 저장되지 않는다.
 *
 * 고정형이 상담을 만들지 않게 된 뒤로, 답변이 빈 버튼은 눌러도 빈 말풍선만 나오고 상담사
 * 쪽에는 흔적조차 남지 않는다. 운영자가 실수를 알아챌 경로가 클릭 지표뿐이라 서버에서 막는다.
 */
@ExtendWith(MockitoExtension.class)
class ChatOperationServiceTest {

    @Mock private ChatMenuMapper menuMapper;
    @Mock private ChatBusinessHourMapper businessHourMapper;
    @Mock private ChatSettingMapper settingMapper;
    @Mock private AdminChatMapper adminChatMapper;
    @Mock private ChatTimeProvider timeProvider;

    @InjectMocks private ChatOperationService service;

    private AdminChatMenuRequest request(ChatAnswerType type, String fixedAnswer) {
        return new AdminChatMenuRequest("CODE", "라벨", type, fixedAnswer, null, true);
    }

    @Test
    @DisplayName("고정 답변 유형인데 본문이 비면 생성을 거부한다")
    void createMenu_고정형_빈답변은_거부한다() {
        assertThatThrownBy(() -> service.createMenu(request(ChatAnswerType.FIXED, "   ")))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_FIXED_ANSWER_REQUIRED);

        verify(menuMapper, never()).insert(any());
    }

    @Test
    @DisplayName("고정 답변 유형인데 본문이 null이면 수정도 거부한다 - 이미 있는 버튼도 비워둘 수 없다")
    void updateMenu_고정형_null답변은_거부한다() {
        assertThatThrownBy(() -> service.updateMenu(1L, request(ChatAnswerType.FIXED, null)))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_FIXED_ANSWER_REQUIRED);

        // 유형 검증이 조회보다 먼저다. 없는 메뉴에 대해서도 같은 이유로 거부되어야 한다.
        verify(menuMapper, never()).update(any());
    }

    @Test
    @DisplayName("상담사 연결 유형은 답변이 비어도 통과한다 - 답할 주체가 사람이라 본문이 필요 없다")
    void createMenu_연결유형은_빈답변을_허용한다() {
        given(menuMapper.selectMaxDisplayOrder()).willReturn(4);

        assertThatCode(() -> service.createMenu(request(ChatAnswerType.AGENT, null)))
                .doesNotThrowAnyException();

        verify(menuMapper).insert(any(ChatMenu.class));
    }
}
