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

}
