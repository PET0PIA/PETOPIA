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
 * 행사 취소 예약 정리 SQL의 모양을 못박는다. 조건 하나가 빠져도 문법은 멀쩡해서
 * 컴파일·단위 테스트로는 안 잡히는데, 결과는 "입장 이력 뒤집기"나 "환불 전 취소"처럼 무겁다.
 */
class ReservationFairCancelMapperSqlContractTest {

    private static final Pattern SELECT_LIVE = Pattern.compile(
            "<select id=\"selectLiveReservationsOfCanceledFairsForUpdate\"[^>]*>(.*?)</select>", Pattern.DOTALL);
    private static final Pattern CANCEL = Pattern.compile(
            "<update id=\"cancelByFairCancellation\"[^>]*>(.*?)</update>", Pattern.DOTALL);

    @Test
    @DisplayName("정리 대상은 취소된 행사의 PENDING_PAYMENT·CONFIRMED 예약뿐이다")
    void selectsOnlyLiveReservationsOfCanceledFairs() throws IOException {
        String sql = extract(SELECT_LIVE);

        assertThat(sql).contains("f.canceled_at IS NOT NULL");
        assertThat(sql).contains("r.status IN ('PENDING_PAYMENT', 'CONFIRMED')");
        // CHECKED_IN이 대상에 들어가면 이미 입장한 예약을 취소로 되돌리게 된다.
        assertThat(sql).doesNotContain("'CHECKED_IN'");
    }

    @Test
    @DisplayName("잠금은 예약 테이블에만 건다(결제·환불은 다른 도메인 소유라 읽기만 한다)")
    void locksOnlyReservationRows() throws IOException {
        assertThat(extract(SELECT_LIVE)).contains("FOR UPDATE OF r SKIP LOCKED");
    }

    @Test
    @DisplayName("취소 UPDATE는 상태 CAS와 행사 취소 여부를 갱신 시점에 다시 확인한다")
    void reChecksStatusAndFairCancellationOnUpdate() throws IOException {
        String sql = extract(CANCEL);

        assertThat(sql).contains("r.status = #{expectedStatus}");
        assertThat(sql).contains("f.canceled_at IS NOT NULL");
        // 사람이 아니라 시스템이 정리한 취소라는 표시. 화면·조회가 이 값으로 자진취소와 구분한다.
        assertThat(sql).contains("r.canceled_by = NULL");
    }

    @Test
    @DisplayName("환불이 필요한 건은 환불 COMPLETED를 갱신 시점에 다시 확인한다")
    void reChecksRefundCompletionWhenRequired() throws IOException {
        String sql = extract(CANCEL);

        assertThat(sql).contains("requireRefundCompleted");
        assertThat(sql).contains("rf.status = 'COMPLETED'");
    }

    @Test
    @DisplayName("건너뛸 예약은 LIMIT 앞에서 걸러낸다 - 보류 건이 배치를 막지 않게")
    void filtersDeferredReservationsBeforeApplyingLimit() throws IOException {
        String sql = extract(SELECT_LIVE);

        // 돈이 움직이는 중인 결제와 환불이 안 끝난 결제는 서비스가 건너뛴다. 그 판정이 LIMIT
        // 뒤에만 있으면, 앞쪽 limit건이 전부 보류일 때 매 주기 같은 행만 집어오고 뒤쪽 취소
        // 가능 예약은 차례가 오지 않는다. 그래서 조회에서 먼저 제외해야 한다.
        String inFlight = "p.status NOT IN ('PENDING', 'WAITING_FOR_DEPOSIT', 'PROCESSING')";
        String refundPending = "p.status <> 'COMPLETED' OR rf.status = 'COMPLETED'";
        assertThat(sql).contains(inFlight);
        assertThat(sql).contains(refundPending);
        assertThat(sql.indexOf(inFlight)).isLessThan(sql.indexOf("LIMIT"));
        assertThat(sql.indexOf(refundPending)).isLessThan(sql.indexOf("LIMIT"));
        // 결제 행이 없는 무료 예약은 두 조건 모두에서 살아남아야 한다(LEFT JOIN이라 NULL).
        assertThat(sql).contains("p.status IS NULL");
    }

    private String extract(Pattern pattern) throws IOException {
        Matcher matcher = pattern.matcher(readMapperXml());
        assertThat(matcher.find()).isTrue();
        return unescape(normalizeWhitespace(matcher.group(1)));
    }

    private String readMapperXml() throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mapper/reservation/ReservationFairCancelMapper.xml")) {
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
