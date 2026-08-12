package com.ms.petopia.api.popup.mapper;

import com.ms.petopia.api.popup.domain.Popup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PopupMapper {
    // 현재 노출 중인 팝업 목록 (공개 API용)
    List<Popup> selectActiveList();

    // 전체 팝업 목록 (관리자용)
    List<Popup> selectAll();

    // 단건 조회
    Popup selectById(@Param("popupId") Long popupId);

    // 등록
    void insert(Popup popup);

    // 수정
    void update(Popup popup);

    // 삭제
    void delete(@Param("popupId") Long popupId);

    // 노출 여부 토글
    void updateActive(@Param("popupId") Long popupId, @Param("isActive") boolean isActive);
}
