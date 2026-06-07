package com.crystal.msgateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MsGatewayApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context starts up without errors
    }
}
