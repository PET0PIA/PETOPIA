package com.ms.petopia.api.reservation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EntryQrTokenServiceTest {

    /** 허용되는 최소 길이(32자) 비밀키. */
    private static final String MIN_LENGTH_SECRET = "0123456789abcdef0123456789abcdef";

    private final EntryQrTokenService service = new EntryQrTokenService(MIN_LENGTH_SECRET);

    @Test
    void createsStableSignedTokenPerReservation() {
        String first = service.tokenForReservation(10L);
        String replay = service.tokenForReservation(10L);
        String other = service.tokenForReservation(11L);

        assertThat(first).isEqualTo(replay);
        assertThat(first).startsWith("v1.r.10.");
        assertThat(other).isNotEqualTo(first);
        assertThat(service.hash(first)).hasSize(64);
    }

    @Test
    @DisplayName("비밀키가 없으면 서비스를 만들지 않는다")
    void rejectsNullSecret() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EntryQrTokenService(null));
    }

    @Test
    @DisplayName("비밀키가 32자보다 짧으면 서비스를 만들지 않는다")
    void rejectsSecretShorterThanMinimumLength() {
        String tooShort = MIN_LENGTH_SECRET.substring(0, 31);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EntryQrTokenService(tooShort));
    }

    @Test
    @DisplayName("비밀키가 정확히 32자면 허용한다")
    void acceptsSecretAtMinimumLength() {
        assertThat(MIN_LENGTH_SECRET).hasSize(32);

        assertThatCode(() -> new EntryQrTokenService(MIN_LENGTH_SECRET))
                .doesNotThrowAnyException();
    }
}
