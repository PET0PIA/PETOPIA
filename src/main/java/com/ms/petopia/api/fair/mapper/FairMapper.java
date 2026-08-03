package com.ms.petopia.api.fair.mapper;

import com.ms.petopia.api.fair.dto.Fair;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * fairs 테이블 매퍼.
 *
 * <p>XML은 {@code mapper/fair/FairMapper.xml}에 있다. 신청 조회 목록, 상태별 검색처럼
 * 특정 API 전용 쿼리는 여기에 넣지 않고 해당 API 작업에서 추가한다. 이 매퍼는 entity 단위
 * 기본 CRUD만 담당한다.
 */
@Mapper
public interface FairMapper {

    /**
     * 신청서를 저장한다. 저장 후 {@code fair.getFairId()}로 생성된 PK를 바로 조회할 수 있다.
     */
    int insert(Fair fair);

    /**
     * fairId로 단건 조회한다. 없으면 null.
     */
    Fair selectById(@Param("fairId") Long fairId);

    /**
     * null이 아닌 필드만 갱신한다. 승인/반려/수정 등 API마다 채우는 필드가 달라서
     * 범용 update 하나로 두고, 호출부에서 필요한 필드만 세팅한 Fair를 넘기는 방식이다.
     * updated_at은 항상 현재 시각으로 갱신된다. applicant_user_id/created_at은 변경 대상이 아니다.
     */
    int update(Fair fair);
}
