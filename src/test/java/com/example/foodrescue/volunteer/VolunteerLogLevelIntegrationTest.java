package com.example.foodrescue.volunteer;

import com.example.foodrescue.ActuatorTestSupport;
import com.example.foodrescue.IntegrationTest;
import com.example.foodrescue.common.DemoData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class VolunteerLogLevelIntegrationTest extends ActuatorTestSupport {

    private static final String LOGGER = "com.example.foodrescue.volunteer";
    private static final String DEBUG_MESSAGE = "Завантаження волонтера " + DemoData.DEMO_VOLUNTEER;
    private static final String DEBUG_LEVEL = "DEBUG";
    private static final String INFO_LEVEL = "INFO";

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void prepareDemoVolunteer() {
        VolunteerTestFixtures.ensureDemoVolunteer(jdbc);
    }

    @AfterEach
    void resetLevel() throws Exception {
        setLevel(LOGGER, null);
    }

    @Test
    void setLevel_WhenDebug_ShouldShowDebugMessageWithoutRestart(CapturedOutput output) throws Exception {
        assertThat(effectiveLevel(LOGGER)).isEqualTo(INFO_LEVEL);
        callVolunteerApi();
        assertThat(output).doesNotContain(DEBUG_MESSAGE);

        setLevel(LOGGER, DEBUG_LEVEL);
        assertThat(effectiveLevel(LOGGER)).isEqualTo(DEBUG_LEVEL);
        callVolunteerApi();

        assertThat(output).contains(DEBUG_MESSAGE);
    }

    @Test
    void setLevel_WhenResetToNull_ShouldHideDebugMessage(CapturedOutput output) throws Exception {
        setLevel(LOGGER, DEBUG_LEVEL);
        assertThat(effectiveLevel(LOGGER)).isEqualTo(DEBUG_LEVEL);
        callVolunteerApi();
        assertThat(output).contains(DEBUG_MESSAGE);
        int outputBeforeReset = output.getOut().length();

        setLevel(LOGGER, null);
        assertThat(effectiveLevel(LOGGER)).isEqualTo(INFO_LEVEL);
        callVolunteerApi();

        assertThat(output.getOut().substring(outputBeforeReset)).doesNotContain(DEBUG_MESSAGE);
    }

    private void callVolunteerApi() throws Exception {
        mvc.perform(get("/api/v1/volunteers/{id}", DemoData.DEMO_VOLUNTEER)
                        .with(user("volunteer1").roles("VOLUNTEER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DemoData.DEMO_VOLUNTEER.toString()));
    }
}
