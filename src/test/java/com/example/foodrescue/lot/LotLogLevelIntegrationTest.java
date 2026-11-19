package com.example.foodrescue.lot;

import com.example.foodrescue.ActuatorTestSupport;
import com.example.foodrescue.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LotLogLevelIntegrationTest extends ActuatorTestSupport {

    private static final String LOGGER = "com.example.foodrescue.lot";
    private static final String DEBUG_MESSAGE = "Завантаження списку лотів";
    private static final String DEBUG_LEVEL = "DEBUG";

    @AfterEach
    void resetLevel() throws Exception {
        setLevel(LOGGER, null);
    }

    @Test
    void setLevel_WhenDebug_ShouldShowDebugMessageWithoutRestart(CapturedOutput output) throws Exception {
        callLotApi();
        assertThat(output).doesNotContain(DEBUG_MESSAGE);

        setLevel(LOGGER, DEBUG_LEVEL);
        assertThat(effectiveLevel(LOGGER)).isEqualTo(DEBUG_LEVEL);
        callLotApi();

        assertThat(output).contains(DEBUG_MESSAGE);
    }

    @Test
    void setLevel_WhenResetToNull_ShouldHideDebugMessage(CapturedOutput output) throws Exception {
        setLevel(LOGGER, DEBUG_LEVEL);
        setLevel(LOGGER, null);

        callLotApi();

        assertThat(output).doesNotContain(DEBUG_MESSAGE);
    }

    private void callLotApi() throws Exception {
        mvc.perform(get("/api/v1/lots").with(user("donor1").roles("DONOR")))
                .andExpect(status().isOk());
    }
}
