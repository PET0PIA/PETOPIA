package com.ms.petopia.api.chat.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 고정형 문의 유형 클릭 집계.
 *
 * <p>고정형이 세션을 만들지 않게 되면서 "어떤 문의가 많은가"를 셀 근거가 함께 사라졌다.
 * 그 축만 되살리는 테이블이라 상담 데이터와 분리돼 있고, 봇 트래픽으로 부풀어도 상담에는
 * 영향이 없다.
 */
@Mapper
public interface ChatMenuClickMapper {

    void insert(@Param("menuId") Long menuId,
                @Param("userId") Long userId,
                @Param("guestKey") String guestKey);
}
