package com.ms.petopia.api.auth.mapper;

import com.ms.petopia.api.auth.domain.FairAdminAssignment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FairAdminAssignmentMapper {

    int insertFairAdminAssignment(FairAdminAssignment assignment);
}
