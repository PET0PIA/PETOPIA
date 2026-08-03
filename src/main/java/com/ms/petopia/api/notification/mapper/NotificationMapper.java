package com.ms.petopia.api.notification.mapper;

import com.ms.petopia.api.notification.dto.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {
    void insert(Notification notification);
    Notification selectById(@Param("notificationId")Long notificationId);
    List<Notification> selectByUserId(@Param("userId") Long userId);
}
