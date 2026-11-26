package com.example.foodrescue.delivery;

import com.example.foodrescue.ActuatorTestSupport;
import com.example.foodrescue.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class DeliveryLogLevelIntegrationTest extends ActuatorTestSupport {

    private static final String LOGGER = "com.example.foodrescue.delivery";
    private static final String DEBUG_MESSAGE = "Завантаження пункту призначення";
    private static final String DEBUG_LEVEL = "DEBUG";
    private static final String INFO_LEVEL = "INFO";

    @Autowired JdbcClient jdbc;
    @Autowired UserDetailsService users;

    @AfterEach
    void resetLevel() throws Exception {
        setLevel(LOGGER, null);
    }

    @Test
    void setLevel_WhenDebug_ShouldShowDebugMessageWithoutRestart(CapturedOutput output) throws Exception {
        UUID pointId = new DeliveryTestFixtures(mvc, jdbc, users).createPoint("BAKERY");
        assertThat(effectiveLevel(LOGGER)).isEqualTo(INFO_LEVEL);
        callDeliveryApi(pointId);
        assertThat(output).doesNotContain(DEBUG_MESSAGE);

        setLevel(LOGGER, DEBUG_LEVEL);
        assertThat(effectiveLevel(LOGGER)).isEqualTo(DEBUG_LEVEL);
        callDeliveryApi(pointId);

        assertThat(output).contains(DEBUG_MESSAGE);
    }

    @Test
    void setLevel_WhenResetToNull_ShouldHideDebugMessage(CapturedOutput output) throws Exception {
        UUID pointId = new DeliveryTestFixtures(mvc, jdbc, users).createPoint("BAKERY");
        setLevel(LOGGER, DEBUG_LEVEL);
        assertThat(effectiveLevel(LOGGER)).isEqualTo(DEBUG_LEVEL);
        setLevel(LOGGER, null);
        assertThat(effectiveLevel(LOGGER)).isEqualTo(INFO_LEVEL);

        callDeliveryApi(pointId);

        assertThat(output).doesNotContain(DEBUG_MESSAGE);
    }

    private void callDeliveryApi(UUID pointId) throws Exception {
        mvc.perform(get("/api/v1/destination-points/{id}", pointId)
                        .with(user("coordinator").roles("COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pointId.toString()));
    }
}
