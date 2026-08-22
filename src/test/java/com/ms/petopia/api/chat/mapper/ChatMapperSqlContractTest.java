package com.ms.petopia.api.chat.mapper;

import com.ms.petopia.api.chat.dto.ChatConversationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상태 판정 SQL의 모양을 못박는다.
 *
 * <p>이 도메인의 상태 판정은 두 곳에 있다. {@link ChatConversationStatus}의 메서드와 매퍼의
 * 조건부 UPDATE·필터. <b>실제 집행은 SQL이 한다</b> - enum은 화면 표시와 응답 DTO에만 쓰인다.
 * 그래서 enum만 고치면 컴파일은 통과하고, "입력창은 열려 있는데 전송은 거부되는" 화면이 만들어진다.
 * 재설계에서 AI_ANSWERED를 AI_HANDLED로 바꿀 때 실제로 빠뜨릴 뻔한 지점이 여기다.
 *
 * <p>기대값을 문자열로 적지 않고 enum에서 뽑아내는 것이 핵심이다. 상태를 하나 추가하면 이
 * 테스트가 먼저 깨지면서 "그 상태를 SQL 목록에 넣을지" 결정하게 만든다.
 */
class ChatMapperSqlContractTest {

    private static final Pattern MARK_WAITING = Pattern.compile(
            "<update id=\"markWaitingAgent\"[^>]*>(.*?)</update>", Pattern.DOTALL);
    private static final Pattern MARK_AI_HANDLED = Pattern.compile(
            "<update id=\"markAiHandled\"[^>]*>(.*?)</update>", Pattern.DOTALL);
    private static final Pattern CLAIM_AI_CALL = Pattern.compile(
            "<update id=\"claimAiCall\"[^>]*>(.*?)</update>", Pattern.DOTALL);
    private static final Pattern COUNT_WAITING = Pattern.compile(
            "<select id=\"countWaiting\"[^>]*>(.*?)</select>", Pattern.DOTALL);
    private static final Pattern STATUS_FILTER = Pattern.compile(
            "<sql id=\"statusFilter\">(.*?)</sql>", Pattern.DOTALL);

    @Test
    @DisplayName("markWaitingAgent는 CLOSED를 뺀 모든 상태를 허용한다 - acceptsUserMessage()와 같은 규칙이어야 한다")
    void markWaitingAgent_허용목록이_enum과_일치한다() throws IOException {
        String sql = extract("mapper/chat/ChatConversationMapper.xml", MARK_WAITING);

        for (ChatConversationStatus status : ChatConversationStatus.values()) {
            boolean listed = sql.contains("'" + status.name() + "'");
            assertThat(listed)
                    .as("%s는 acceptsUserMessage()=%s이므로 허용 목록에 %s",
                            status, status.acceptsUserMessage(),
                            status.acceptsUserMessage() ? "있어야 한다" : "없어야 한다")
                    .isEqualTo(status.acceptsUserMessage());
        }
    }

    @Test
    @DisplayName("countWaiting은 needsAgentReply()가 참인 상태만 센다 - 배지 숫자가 실제 업무량과 어긋나면 안 된다")
    void countWaiting_대기정의가_enum과_일치한다() throws IOException {
        String sql = extract("mapper/chat/AdminChatMapper.xml", COUNT_WAITING);

        for (ChatConversationStatus status : ChatConversationStatus.values()) {
            assertThat(sql.contains("'" + status.name() + "'"))
                    .as("%s는 needsAgentReply()=%s", status, status.needsAgentReply())
                    .isEqualTo(status.needsAgentReply());
        }
    }

    @Test
    @DisplayName("대기열 OPEN 필터에 AI_HANDLED가 섞이지 않는다 - 이미 답이 나간 대화를 미답변으로 읽게 된다")
    void statusFilter_OPEN에_AI_HANDLED가_없다() throws IOException {
        String sql = extract("mapper/chat/AdminChatMapper.xml", STATUS_FILTER);
        String openBranch = between(sql, "AdminChatFilter@OPEN", "</when>");

        assertThat(openBranch).contains("'WAITING_AGENT'", "'IN_PROGRESS'");
        assertThat(openBranch).doesNotContain("'AI_HANDLED'");
        // 별도 탭으로는 볼 수 있어야 한다. 안 보이면 자동 응대는 아무도 검수하지 않는 채널이 된다.
        assertThat(sql).contains("AdminChatFilter@AI_HANDLED");
    }

    @Test
    @DisplayName("markAiHandled는 WAITING_AGENT에서만 전이한다 - 상담사가 먼저 답한 상담을 대기열에서 지우면 안 된다")
    void markAiHandled_경쟁조건_가드가_남아있다() throws IOException {
        String sql = extract("mapper/chat/ChatConversationMapper.xml", MARK_AI_HANDLED);

        assertThat(sql).contains("status = 'WAITING_AGENT'");
    }

    @Test
    @DisplayName("claimAiCall은 횟수가 아니라 stale 시각으로 판정한다 - 한도가 되살아나면 안 된다")
    void claimAiCall_횟수한도가_아니다() throws IOException {
        String sql = extract("mapper/chat/ChatConversationMapper.xml", CLAIM_AI_CALL);

        // 죽은 선점이 스스로 풀려야 한다. 이 조건이 빠지면 반납 실패 한 번이 그 대화의
        // 자동 응대를 영구히 막는다.
        assertThat(sql).contains("ai_call_started_at IS NULL", "ai_call_started_at <");
        // ai_answer_count는 지표 카운터로만 남았다. 여기 다시 들어오면 한도 집행이 부활한다.
        assertThat(sql).doesNotContain("ai_answer_count");
    }

    @Test
    @DisplayName("옛 상태값이 SQL 어디에도 남아 있지 않다")
    void 옛_상태값이_남아있지_않다() throws IOException {
        for (String resource : List.of("mapper/chat/ChatConversationMapper.xml",
                "mapper/chat/AdminChatMapper.xml")) {
            assertThat(readResource(resource))
                    .as("%s에 AI_ANSWERED가 남아 있다", resource)
                    .doesNotContain("'AI_ANSWERED'");
        }
    }

    private String between(String source, String from, String to) {
        int start = source.indexOf(from);
        assertThat(start).as("'%s'를 찾을 수 없다", from).isNotNegative();
        int end = source.indexOf(to, start);
        assertThat(end).as("'%s' 뒤에서 '%s'를 찾을 수 없다", from, to).isNotNegative();
        return source.substring(start, end);
    }

    private String extract(String resource, Pattern pattern) throws IOException {
        Matcher matcher = pattern.matcher(readResource(resource));
        assertThat(matcher.find()).as("%s에서 statement를 찾을 수 없다", resource).isTrue();
        return unescape(normalizeWhitespace(matcher.group(1)));
    }

    private String readResource(String resource) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as("%s를 읽을 수 없다", resource).isNotNull();
            return unescape(new String(input.readAllBytes(), StandardCharsets.UTF_8));
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
