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
 * 정원 정합성이 걸려 있는 SQL의 모양을 못박는다.
 *
 * <p>예전에는 "정원 집계에서 현장예매를 제외하는지"를 봤다. 지금은 집계 자체가 없고
 * {@code fair_dates.reserved_count}를 조건부 UPDATE로 직접 움직이므로, 지켜야 할 것이
 * 달라졌다 — <b>판정 조건이 UPDATE 문장 안에 있어야 한다.</b>
 *
 * <p>현장예매 정원(V49)도 같은 규칙을 따른다. 다만 카운터가 {@code onsite_sales_policies}에
 * 따로 있어서, 실수로 사전예약 정원을 움직이지 않는지까지 함께 못박는다.
 *
 * <p>이걸 애플리케이션에서 미리 조회해 분기하는 형태로 바꾸면 조회와 차감 사이에 창이 생겨
 * 정원이 초과된다. 문법이 깨지지 않아 컴파일·단위 테스트로는 안 잡히는 종류의 회귀라
 * SQL 본문을 직접 검사한다.
 */
class ReservationCapacityMapperSqlContractTest {

    private static final Pattern OCCUPY = Pattern.compile(
            "<update id=\"occupy\"[^>]*>(.*?)</update>", Pattern.DOTALL);
    private static final Pattern RELEASE = Pattern.compile(
            "<update id=\"release\"[^>]*>(.*?)</update>", Pattern.DOTALL);
    private static final Pattern OCCUPY_ONSITE = Pattern.compile(
            "<update id=\"occupyOnsite\"[^>]*>(.*?)</update>", Pattern.DOTALL);
    private static final Pattern RELEASE_ONSITE = Pattern.compile(
            "<update id=\"releaseOnsite\"[^>]*>(.*?)</update>", Pattern.DOTALL);

    @Test
    @DisplayName("정원 점유는 판정과 증가를 한 문장에서 처리한다")
    void occupy_판정과증가가한문장이다() throws IOException {
        String sql = extract(OCCUPY);

        assertThat(sql).contains("reserved_count = reserved_count + 1");
        // 이 조건이 빠지면 정원을 넘겨서도 점유가 성공한다.
        assertThat(sql).contains("reserved_count < capacity");
    }

    @Test
    @DisplayName("정원 반납은 0 아래로 내려가지 않는다")
    void release_음수로내려가지않는다() throws IOException {
        String sql = extract(RELEASE);

        assertThat(sql).contains("reserved_count = reserved_count - 1");
        // CHECK 제약 위반 방어이자 같은 예약을 두 번 반납하는 사고의 방어선이다.
        assertThat(sql).contains("reserved_count > 0");
    }

    @Test
    @DisplayName("현장예매 정원 점유도 판정과 증가를 한 문장에서 처리한다")
    void occupyOnsite_판정과증가가한문장이다() throws IOException {
        String sql = extract(OCCUPY_ONSITE);

        assertThat(sql).contains("reserved_count = osp.reserved_count + 1");
        // 이 조건이 빠지면 정원을 넘겨서도 점유가 성공한다.
        assertThat(sql).contains("osp.reserved_count < osp.capacity");
        // capacity가 비어 있으면(제한 없음) 언제나 통과해야 한다 - 정원을 걸지 않은 기존
        // 행사가 이 기능 추가만으로 매진되면 안 된다.
        assertThat(sql).contains("osp.capacity IS NULL");
        // 사전예약 정원(fair_dates)을 건드리면 두 도메인이 서로 자리를 뺏는다.
        assertThat(sql).contains("UPDATE onsite_sales_policies");
    }

    @Test
    @DisplayName("현장예매 정원 반납도 0 아래로 내려가지 않는다")
    void releaseOnsite_음수로내려가지않는다() throws IOException {
        String sql = extract(RELEASE_ONSITE);

        assertThat(sql).contains("reserved_count = osp.reserved_count - 1");
        assertThat(sql).contains("osp.reserved_count > 0");
        assertThat(sql).contains("UPDATE onsite_sales_policies");
    }

    private String extract(Pattern pattern) throws IOException {
        Matcher matcher = pattern.matcher(readMapperXml());
        assertThat(matcher.find()).isTrue();
        return unescape(normalizeWhitespace(matcher.group(1)));
    }

    private String readMapperXml() throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mapper/reservation/ReservationCapacityMapper.xml")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** XML이라 부등호가 엔티티로 이스케이프돼 있다. 비교 전에 되돌린다. */
    private String unescape(String sql) {
        return sql.replace("&lt;", "<").replace("&gt;", ">");
    }

    private String normalizeWhitespace(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
