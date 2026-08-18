package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.dto.AdminChatMenuRequest;
import com.ms.petopia.api.chat.dto.AdminChatMenuResponse;
import com.ms.petopia.api.chat.dto.ChatBusinessHour;
import com.ms.petopia.api.chat.dto.ChatMenuStat;
import com.ms.petopia.api.chat.dto.ChatSetting;
import com.ms.petopia.api.chat.entity.ChatMenu;
import com.ms.petopia.api.chat.mapper.AdminChatMapper;
import com.ms.petopia.api.chat.mapper.ChatBusinessHourMapper;
import com.ms.petopia.api.chat.mapper.ChatMenuMapper;
import com.ms.petopia.api.chat.mapper.ChatSettingMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 운영자가 배포 없이 바꾸는 것들 - 버튼, 운영시간, 문구.
 *
 * <p>이 화면이 없으면 문구 한 줄 고치는 데 배포가 필요하고, 그러면 결국 아무도 고치지 않아
 * 초기 시딩 문구가 그대로 운영에 남는다.
 */
@Service
@RequiredArgsConstructor
public class ChatOperationService {

    /** 지표 집계 기간. 상담 패턴은 계절을 타므로 너무 길게 잡으면 지금 상태가 안 보인다. */
    private static final int STATS_DAYS = 30;

    private final ChatMenuMapper menuMapper;
    private final ChatBusinessHourMapper businessHourMapper;
    private final ChatSettingMapper settingMapper;
    private final AdminChatMapper adminChatMapper;
    private final ChatTimeProvider timeProvider;

    // ----- 버튼 -----

    @Transactional(readOnly = true)
    public List<AdminChatMenuResponse> listMenus() {
        return AdminChatMenuResponse.fromAll(menuMapper.selectAllMenus());
    }

    @Transactional
    public AdminChatMenuResponse createMenu(AdminChatMenuRequest request) {
        ChatMenu menu = ChatMenu.builder()
                .code(request.code())
                .label(request.label())
                .answerType(request.answerType())
                .fixedAnswer(request.fixedAnswer())
                .aiContext(request.aiContext())
                // 순서를 안 주면 맨 뒤에 붙인다. 0으로 두면 새 버튼이 맨 위로 올라가는데,
                // 운영자가 기대하는 동작이 아니다(추가했더니 1번 버튼이 바뀌어 있다).
                .displayOrder(request.displayOrder() != null
                        ? request.displayOrder()
                        : menuMapper.selectMaxDisplayOrder() + 1)
                .isActive(request.isActive() == null || request.isActive())
                .build();
        try {
            menuMapper.insert(menu);
        } catch (DuplicateKeyException e) {
            // code는 대화가 참조하는 식별자라 중복되면 안 된다. 유니크 제약이 최종 방어선이고,
            // 여기서는 그 실패를 사용자가 이해할 수 있는 메시지로 바꿔준다.
            throw new CommonException(ErrorCode.CHAT_MENU_CODE_DUPLICATED, e);
        }
        return AdminChatMenuResponse.from(menu);
    }

    @Transactional
    public AdminChatMenuResponse updateMenu(Long menuId, AdminChatMenuRequest request) {
        ChatMenu existing = menuMapper.selectById(menuId);
        if (existing == null) {
            throw new CommonException(ErrorCode.CHAT_MENU_NOT_FOUND);
        }

        existing.setLabel(request.label());
        existing.setAnswerType(request.answerType());
        existing.setFixedAnswer(request.fixedAnswer());
        existing.setAiContext(request.aiContext());
        existing.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : existing.getDisplayOrder());
        existing.setIsActive(request.isActive() == null || request.isActive());
        menuMapper.update(existing);

        return AdminChatMenuResponse.from(existing);
    }

    /** 삭제가 아니라 비활성이다. 이유는 {@link ChatMenuMapper#deactivate} 주석 참고. */
    @Transactional
    public void deactivateMenu(Long menuId) {
        if (menuMapper.deactivate(menuId) != 1) {
            throw new CommonException(ErrorCode.CHAT_MENU_NOT_FOUND);
        }
    }

    /** 화면에 보이는 순서대로 받은 ID 목록으로 순서를 다시 매긴다. */
    @Transactional
    public void reorderMenus(List<Long> menuIds) {
        for (int i = 0; i < menuIds.size(); i++) {
            menuMapper.updateDisplayOrder(menuIds.get(i), i + 1);
        }
    }

    // ----- 운영시간 -----

    @Transactional(readOnly = true)
    public List<ChatBusinessHour> listBusinessHours() {
        return businessHourMapper.selectAll();
    }

    @Transactional
    public void saveBusinessHours(List<ChatBusinessHour> hours) {
        for (ChatBusinessHour hour : hours) {
            if (!hour.startTime().isBefore(hour.endTime())) {
                // DB의 CHECK 제약과 같은 규칙이지만, 여기서 먼저 걸러야 어느 요일이 잘못됐는지
                // 알려줄 수 있다. 제약 위반 예외만으로는 화면에서 그 정보를 복원할 수 없다.
                throw new CommonException(ErrorCode.CHAT_BUSINESS_HOUR_INVALID,
                        "시작 시각이 종료 시각보다 빨라야 해요. (요일 " + hour.dayOfWeek() + ")");
            }
            businessHourMapper.upsert(new ChatBusinessHour(
                    hour.dayOfWeek(), hour.startTime(), hour.endTime(),
                    hour.isActive() == null || hour.isActive()));
        }
    }

    // ----- 문구 -----

    @Transactional(readOnly = true)
    public List<ChatSetting> listSettings() {
        return settingMapper.selectAll();
    }

    @Transactional
    public void saveSettings(List<ChatSetting> settings) {
        for (ChatSetting setting : settings) {
            // 없는 키는 조용히 넘긴다. 화면이 보내온 키 중 서버가 모르는 게 있어도 나머지
            // 저장까지 실패시킬 이유는 없다.
            settingMapper.updateValue(setting.settingKey(), setting.settingValue());
        }
    }

    // ----- 지표 -----

    @Transactional(readOnly = true)
    public List<ChatMenuStat> menuStats() {
        return adminChatMapper.selectMenuStats(timeProvider.now().minusDays(STATS_DAYS));
    }
}
