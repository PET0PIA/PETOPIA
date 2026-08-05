package com.ms.petopia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "jwt.secret=c2VjdXJlLXRlc3Qta2V5LXRlc3Qta2V5LXRlc3Qta2V5LXRlc3Qta2V5",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000",
        "petopia.toss.secret-key=test-secret"
})
class PetopiaApplicationTests {

    @Test
    void contextLoads() {
    }

}
