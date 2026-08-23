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
 * 방문일 변경 SQL의 모양을 못박는다.
 *
 * <p>변경 마감은 행사가 정한다({@code fairs.reservation_change_deadline_hours}). 이 조회가
 * 사라지면 서비스는 기본값 12시간으로만 판단하게 되는데, 행사 신청서에 입력한 기한이 화면에는
 * 그대로 보이므로 "48시간으로 설정했는데 12시간 만에 막힌다"는 어긋남이 조용히 생긴다.
 *
 * <p>잠금 범위도 함께 고정한다. 예약 조회는 {@code FOR UPDATE}라 여기에 fairs를 조인하면
 * 행사 행까지 잠긴다 - 그래서 기한은 잠그지 않는 별도 SELECT로 읽는다.
 */
class ReservationChangeMapperSqlContractTest {

    private static final Pattern SELECT_RESERVATION = Pattern.compile(
            "<select id=\"selectReservationForUpdate\"[^>]*>(.*?)</select>", Pattern.DOTALL);
    private static final Pattern SELECT_DEADLINE = Pattern.compile(
            "<select id=\"selectChangeDeadlineHours\"[^>]*>(.*?)</select>", Pattern.DOTALL);

    @Test
    @DisplayName("변경 마감 시간은 행사 설정에서 읽는다")
    void selectChangeDeadlineHours_행사설정을읽는다() throws IOException {
        String sql = extract(SELECT_DEADLINE);

        assertThat(sql).contains("reservation_change_deadline_hours");
        assertThat(sql).contains("FROM fairs");
    }

    @Test
    @DisplayName("기한 조회는 잠그지 않는다")
    void selectChangeDeadlineHours_잠그지않는다() throws IOException {
        // 읽기만 하는 값이다. 여기에 FOR UPDATE가 붙으면 같은 행사의 변경 요청이 줄을 서게 된다.
        assertThat(extract(SELECT_DEADLINE)).doesNotContain("FOR UPDATE");
    }

    @Test
    @DisplayName("예약 조회는 잠금 범위를 예약 테이블로 좁게 유지한다(fairs를 조인하지 않는다)")
    void selectReservationForUpdate_행사를조인하지않는다() throws IOException {
        String sql = extract(SELECT_RESERVATION);

        assertThat(sql).contains("FOR UPDATE");
        // 조인하면서 FOR UPDATE를 그대로 두면 행사 행까지 잠긴다. 굳이 조인하려면
        // 취소 경로(ReservationCancellationMapper)처럼 FOR UPDATE OF로 대상을 한정해야 한다.
        assertThat(sql).doesNotContain("fairs");
    }

    private String extract(Pattern pattern) throws IOException {
        Matcher matcher = pattern.matcher(readMapperXml());
        assertThat(matcher.find()).isTrue();
        return unescape(normalizeWhitespace(matcher.group(1)));
    }

    private String readMapperXml() throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mapper/reservation/ReservationChangeMapper.xml")) {
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
