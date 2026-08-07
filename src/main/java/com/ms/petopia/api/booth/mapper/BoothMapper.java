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

    // 신청 ID로 부스 조회 (1:1) - 이미 생성됐는지 확인, 취소 승인 시 삭제 대상 찾을 때 사용
    Booth selectByApplicationId(@Param("applicationId") Long applicationId);

    // 부스 생성 (신청 확정 시 자동 생성)
    void insertBooth(Booth booth);

    // 신청 ID 기준으로 부스 삭제 (취소 승인 시, 이전 상태가 CONFIRMED였을 때)
    int deleteBoothByApplicationId(@Param("applicationId") Long applicationId);

}
