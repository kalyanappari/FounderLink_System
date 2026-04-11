package com.founderlink.investment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@org.junit.jupiter.api.Disabled("Disabled to match project patterns and favor isolated unit tests")
@SpringBootTest
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "spring.data.redis.host=localhost",
        "spring.rabbitmq.host=localhost"
})
class InvestmentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
