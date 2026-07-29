package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.DbHealth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * DB 연결 점검용 매퍼.
 *
 * <p>업무 테이블에 의존하지 않도록 DUAL만 조회한다. 스키마가 아직 비어 있어도 동작한다.
 *
 * <p>{@code @MapperScan}을 두지 않았으므로 {@code @Mapper}가 붙은 인터페이스만 빈으로 등록된다.
 * XML은 {@code classpath:mapper/**}/*.xml 규칙에 따라 {@code mapper/reservation/HealthCheckMapper.xml}에 있다.
 */
@Mapper
public interface HealthCheckMapper {

    /**
     * DUAL에서 커넥션 상태와 DB 메타 정보를 한 번에 조회한다.
     */
    DbHealth selectDbHealth();

    /**
     * 파라미터 바인딩(PreparedStatement)이 정상인지 확인한다.
     * 넘긴 값이 그대로 돌아오면 바인딩까지 정상이다.
     */
    String selectEcho(@Param("value") String value);
}
