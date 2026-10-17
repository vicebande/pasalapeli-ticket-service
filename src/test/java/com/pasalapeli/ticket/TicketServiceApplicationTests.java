package com.pasalapeli.ticket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/test_db",
    "services.movie-service.url=http://localhost:8082"
})
class TicketServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
