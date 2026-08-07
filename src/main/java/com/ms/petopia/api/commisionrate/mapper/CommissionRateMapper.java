package com.ms.petopia.api.commisionrate.mapper;

import com.ms.petopia.api.commisionrate.dto.CommissionRateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommissionRateMapper {

    /** GLOBAL 스코프의 최신(updated_at 최대) 행. 한 번도 설정 안 했으면 null. */
    CommissionRateRow selectLatestGlobal();

    /** 특정 행사의 FAIR override 최신 행. override 없으면 null. */
    CommissionRateRow selectLatestByFair(@Param("fairId") Long fairId);

    /** 이력 보존형 - UPDATE 대신 매번 새 행 INSERT. */
    void insert(CommissionRateRow row);
}
