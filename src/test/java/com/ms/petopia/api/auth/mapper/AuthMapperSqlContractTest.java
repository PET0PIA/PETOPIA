package com.ms.petopia.api.auth.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AuthMapperSqlContractTest {

    private static final Pattern UPDATE_UNVERIFIED_USER_QUERY = Pattern.compile(
            "<update id=\"updateUnverifiedUser\"[^>]*>(.*?)</update>",
            Pattern.DOTALL
    );

    //email_verified = FALSE만으로는 "미완성 이메일가입"과 "OAuth로 만든 유저(emailVerified가
    //provider 값을 따라가서 false일 수 있음)"를 구분 못 함 - password_hash IS NOT NULL이 없으면
    //네이버 등으로 가입한 진짜 유저 row가 같은 이메일로 일반 가입을 시도하는 다른 사람의 값으로
    //덮어써질 수 있다(계정 탈취/데이터 손상). OAuth 유저는 passwordHash가 항상 null이라 이
    //조건으로 걸러진다.
    @Test
    @DisplayName("미인증 row 재사용 갱신은 비밀번호 해시가 있는(=이메일가입으로 만들어진) row만 대상으로 한다")
    void updateUnverifiedUser_passwordHash가있는row만_대상으로한다() throws IOException {
        String mapperXml = readMapperXml();
        Matcher matcher = UPDATE_UNVERIFIED_USER_QUERY.matcher(mapperXml);

        assertThat(matcher.find()).isTrue();
        String where = normalizeWhitespace(matcher.group(1));
        assertThat(where).contains("email_verified = FALSE");
        assertThat(where).contains("password_hash IS NOT NULL");
    }

    private String readMapperXml() throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("mapper/auth/AuthMapper.xml")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String normalizeWhitespace(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
