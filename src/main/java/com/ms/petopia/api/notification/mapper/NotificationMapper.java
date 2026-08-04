package com.ms.petopia.api.notification.mapper;

import com.ms.petopia.api.notification.dto.NotificationListRow;
import com.ms.petopia.api.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {
    void insert(Notification notification);
    Notification selectById(@Param("notificationId")Long notificationId);

    List<NotificationListRow> selectByUserId(@Param("userId") Long userId,
                                             @Param("offset") long offset,
                                             @Param("limit") int limit);
    Long countByUserId(@Param("userId") Long userId);
}
