package com.example.foodrescue;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "foodrescue.alerts.timeout=500ms")
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, AlertsStubConfiguration.class})
@Sql(scripts = {"/sql/reset-delivery.sql", "/sql/reset-volunteer.sql", "/sql/reset-lot.sql"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public @interface E2ETest {
}
