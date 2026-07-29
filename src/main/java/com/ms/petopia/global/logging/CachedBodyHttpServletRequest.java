package com.ms.petopia.global.logging;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 요청 바디를 미리 통째로 읽어 캐싱하는 래퍼.
 *
 * <p>{@code getInputStream()}은 한 번만 읽을 수 있기 때문에, 필터에서 로깅용으로 읽어버리면
 * Controller의 {@code @RequestBody}가 빈 값을 받는다. 이 래퍼는 생성 시점에 원본 스트림을
 * byte[]로 복사해두고, 이후 호출마다 <b>새 스트림</b>을 만들어 돌려주므로 필터와 Controller가
 * 각각 온전한 바디를 읽을 수 있다.
 *
 * <p>주의: 바디 전체를 메모리에 올리므로 파일 업로드(multipart) 같은 대용량 요청에는 사용하지 않는다.
 * 적용 대상 판단은 {@link HttpLoggingFilter}가 한다.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    public byte[] getCachedBody() {
        return cachedBody;
    }

    public String getBodyAsString() {
        return new String(cachedBody, resolveCharset());
    }

    private Charset resolveCharset() {
        String encoding = getCharacterEncoding();
        if (encoding == null) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream source = new ByteArrayInputStream(cachedBody);

        return new ServletInputStream() {
            @Override
            public int read() {
                return source.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return source.read(b, off, len);
            }

            @Override
            public int available() {
                return source.available();
            }

            @Override
            public boolean isFinished() {
                return source.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("non-blocking read는 지원하지 않습니다.");
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), resolveCharset()));
    }
}
