package com.example.transporterlanes;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TransporterLanesApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
        // Fails if any bean (web, JPA, Flyway, springdoc) cannot be wired.
    }

    @Test
    void testProfileUsesInMemoryH2() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
        }
    }
}
