package com.ms.petopia.api.pet.mapper;

import com.ms.petopia.api.pet.domain.PetAllergyType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * pet_allergy_types 마스터 테이블 매퍼. XML은 {@code mapper/pet/PetAllergyTypeMapper.xml}에 있다.
 */
@Mapper
public interface PetAllergyTypeMapper {

    /** 활성 항목만, 카테고리·정렬순으로. 등록·수정 화면의 선택지 조회(공개 API)에서 쓴다. */
    List<PetAllergyType> selectActive();

    /**
     * 클라이언트가 보낸 allergyTypeId들이 실제로 존재하는지 한 번에 확인하기 위해 조회한다.
     * 존재하지 않는 id는 결과에서 빠지므로 서비스가 요청 개수와 비교해 걸러낸다.
     * 비활성 항목도 함께 반환한다 - "없는 항목"과 "더는 못 고르는 항목"을 다른 오류로
     * 구분해서 알려주기 위해서다(리뷰 도메인의 태그 검증과 같은 방식).
     */
    List<PetAllergyType> selectByIds(@Param("allergyTypeIds") List<Long> allergyTypeIds);
}
