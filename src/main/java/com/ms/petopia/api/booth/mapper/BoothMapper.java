package com.ms.petopia.api.booth.mapper;

import com.ms.petopia.api.booth.domain.Booth;
import com.ms.petopia.api.booth.domain.BoothItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BoothMapper {

    // 부스 ID로 조회
    Booth selectById(@Param("boothId") Long boothId);

    // 부스에 속한 상품·이벤트 목록 조회
    List<BoothItem> selectItemsByBoothId(@Param("boothId") Long boothId);

    // 부스 생성 (신청 확정 시 자동 생성)
    void insertBooth(Booth booth);

    // 신청 ID 기준으로 부스 삭제 (취소 승인 시, 이전 상태가 CONFIRMED였을 때)
    void deleteBoothByApplicationId(@Param("applicationId") Long applicationId);

    // 부스 프로필 부분 수정 (null이 아닌 필드만 갱신)
    void updateBooth(Booth booth);

    // 판매상품·이벤트 등록
    void insertBoothItem(BoothItem item);

}
