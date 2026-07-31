package com.ms.petopia.api.business.mapper;

import com.ms.petopia.api.business.domain.Business;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BusinessMapper {

    void insertBusiness(Business business);

}
