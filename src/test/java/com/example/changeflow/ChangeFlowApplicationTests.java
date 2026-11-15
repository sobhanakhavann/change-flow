package com.example.changeflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.example.changeflow.support.SqliteTestSupport;

@SpringBootTest
class ChangeFlowApplicationTests {
    private static final java.nio.file.Path FILE = SqliteTestSupport.newFile();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("changeflow.database.path", FILE::toString);
    }

    @Test
    void contextLoads() {
    }

}
