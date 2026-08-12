package com.ms.petopia.api.banner.mapper;

import com.ms.petopia.api.banner.domain.Banner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface BannerMapper {

    // 현재 노출 중인 배너 목록 (공개 API용 - 기간 필터, sort_order 순)
    List<Banner> selectActiveList();

    // 전체 배너 목록 (관리자용)
    List<Banner> selectAll();

    // 단건 조회
    Banner selectById(@Param("bannerId") Long bannerId);

    // 등록
    void insert(Banner banner);

    // 수정
    void update(Banner banner);

    // 삭제
    void delete(@Param("bannerId") Long bannerId);

    // 노출 여부 토글
    void updateActive(@Param("bannerId") Long bannerId, @Param("isActive") boolean isActive);

    // 순서 변경 (단건)
    void updateOrder(@Param("bannerId") Long bannerId, @Param("sortOrder") int sortOrder);
}
