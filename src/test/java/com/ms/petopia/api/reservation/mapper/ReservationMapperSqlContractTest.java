package com.ms.petopia.api.reservation.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내 예약 목록 SQL의 모양을 못박는다.
 *
 * <p>목록(selectMyReservations)과 개수(countMyReservations)는 만료 제외 조건이 항상 같아야 한다.
 * 한쪽만 걸면 "총 12건"이라면서 10건만 보이는 상태가 되고, 문법이 깨지지 않아 컴파일·단위
 * 테스트로는 안 잡힌다.
 *
 * <p>예전에는 이 제외를 프론트에서 했다. 그러면 만료 건이 페이지 정원을 차지해 그 뒤의 유효한
 * 예약이 화면에 나올 방법이 없었다(목록 화면은 첫 페이지만 받고, 마이페이지 카드는 5건만 받는다).
 * 그래서 서버로 옮겼고, 다시 빠지면 안 된다.
 */
class ReservationMapperSqlContractTest {

    private static final Pattern SELECT_MY = Pattern.compile(
            "<select id=\"selectMyReservations\"[^>]*>(.*?)</select>", Pattern.DOTALL);
    private static final Pattern COUNT_MY = Pattern.compile(
            "<select id=\"countMyReservations\"[^>]*>(.*?)</select>", Pattern.DOTALL);

    @Test
    @DisplayName("내 예약 목록은 만료 예약을 SQL에서 제외한다")
    void selectMyReservations_만료를제외한다() throws IOException {
        String sql = extract(SELECT_MY);

        assertThat(sql).contains("status != 'EXPIRED'");
    }

    @Test
    @DisplayName("내 예약 개수도 목록과 같은 조건으로 센다")
    void countMyReservations_목록과같은조건이다() throws IOException {
        String sql = extract(COUNT_MY);

        // 이 조건이 빠지면 목록에 보이는 건수와 totalElements·hasNext가 어긋난다.
        assertThat(sql).contains("status != 'EXPIRED'");
    }

    @Test
    @DisplayName("취소 예약은 목록에서 제외하지 않는다")
    void selectMyReservations_취소는제외하지않는다() throws IOException {
        // 취소된 예약은 상세에서 결제 ID·결제수단으로 결제·환불 내역을 확인할 수 있어야 하므로
        // 의도적으로 목록에 남긴다. 다시 숨기려면 이 테스트를 먼저 고쳐야 한다(그때 이 이유를 볼 것).
        assertThat(extract(SELECT_MY)).doesNotContain("'CANCELED'");
        assertThat(extract(COUNT_MY)).doesNotContain("'CANCELED'");
    }

    private String extract(Pattern pattern) throws IOException {
        Matcher matcher = pattern.matcher(readMapperXml());
        assertThat(matcher.find()).isTrue();
        return unescape(normalizeWhitespace(matcher.group(1)));
    }

    private String readMapperXml() throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mapper/reservation/ReservationMapper.xml")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** XML이라 부등호가 엔티티로 이스케이프돼 있을 수 있다. 비교 전에 되돌린다. */
    private String unescape(String sql) {
        return sql.replace("&lt;", "<").replace("&gt;", ">");
    }

    private String normalizeWhitespace(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
