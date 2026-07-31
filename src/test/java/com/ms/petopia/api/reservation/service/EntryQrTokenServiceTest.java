package com.ms.petopia.api.reservation.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntryQrTokenServiceTest {

    private final EntryQrTokenService service = new EntryQrTokenService(
            "0123456789abcdef0123456789abcdef"
    );

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
}
