package com.ms.petopia.api.business.mapper;

import com.ms.petopia.api.business.domain.Business;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BusinessMapper {

    // 사업자 등록. insert 후 business.businessId에 생성된 PK가 채워짐(useGeneratedKeys)
    void insertBusiness(Business business);

    // 내 사업자 목록 조회
    List<Business> selectByOwnerId(Long ownerId);

    // 사업자 상세 조회
    Business selectById(Long businessId);

}
