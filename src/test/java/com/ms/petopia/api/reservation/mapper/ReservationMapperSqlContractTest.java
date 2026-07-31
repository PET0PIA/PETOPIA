package com.ms.petopia.api.reservation.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationMapperSqlContractTest {

    private static final Pattern CAPACITY_QUERY = Pattern.compile(
            "<select id=\"countCapacityOccupyingReservations\"[^>]*>(.*?)</select>",
            Pattern.DOTALL
    );

    @Test
    @DisplayName("사전예약 정원 집계에서 현장 직접예매를 제외한다")
    void countCapacityOccupyingReservations_사전예약유형만집계한다() throws IOException {
        String mapperXml = readMapperXml();
        Matcher matcher = CAPACITY_QUERY.matcher(mapperXml);

        assertThat(matcher.find()).isTrue();
        assertThat(normalizeWhitespace(matcher.group(1)))
                .contains("reservation_type = 'ADVANCE'");
    }

    private String readMapperXml() throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mapper/reservation/ReservationMapper.xml")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String normalizeWhitespace(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
