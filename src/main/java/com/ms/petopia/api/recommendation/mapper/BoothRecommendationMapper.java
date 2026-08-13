package com.ms.petopia.api.recommendation.mapper;

import com.ms.petopia.api.recommendation.domain.BoothCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

//부스 추천용 읽기 전용 매퍼. booth/booth_item/fairs 테이블을 SELECT만 한다
@Mapper
public interface BoothRecommendationMapper {

    //fairId가 실제 존재하는 행사인지 확인
    boolean existsFair(@Param("fairId") Long fairId);

    //그 행사의 부스 + 아이템 목록을 한 번에 조회
    List<BoothCandidate> selectBoothCandidates(@Param("fairId") Long fairId);
}
