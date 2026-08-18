package com.ms.petopia.api.chat.mapper;

import com.ms.petopia.api.chat.dto.ChatSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSettingMapper {

    String selectValue(@Param("settingKey") String settingKey);

    List<ChatSetting> selectAll();

    /** 값만 바꾼다. 키와 설명은 마이그레이션이 정한 것이라 화면에서 바꾸지 않는다. */
    int updateValue(@Param("settingKey") String settingKey, @Param("settingValue") String settingValue);
}
