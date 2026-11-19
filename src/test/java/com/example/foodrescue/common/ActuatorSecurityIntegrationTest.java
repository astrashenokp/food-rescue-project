package com.example.foodrescue.common;

import com.example.foodrescue.ActuatorTestSupport;
import com.example.foodrescue.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ActuatorSecurityIntegrationTest extends ActuatorTestSupport {

    private static final String LOT_LOGGER = "com.example.foodrescue.lot";
    private static final String LOT_LOGGER_PATH = LOGGERS_PATH + "/" + LOT_LOGGER;
    private static final String DONOR_USER = "donor1";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String DONOR_ROLE = "DONOR";
    private static final String LOGIN_URL = "/login";
    private static final String STATUS_PATH = "$.status";
    private static final String COMPONENTS_PATH = "$.components";
    private static final String INFO_LEVEL = "INFO";

    @Value("${foodrescue.demo.passwords.donor1}")
    private String donorPassword;

    @Test
    void health_WhenAnonymous_ShouldReturnUpWithoutComponents() throws Exception {
        mvc.perform(get(HEALTH_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath(STATUS_PATH).value("UP"))
                .andExpect(jsonPath(COMPONENTS_PATH).doesNotExist());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    void health_WhenAdmin_ShouldReturnComponents() throws Exception {
        mvc.perform(get(HEALTH_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.ping.status").value("UP"));
    }

    @Test
    void loggers_WhenAnonymous_ShouldReturnUnauthorized() throws Exception {
        mvc.perform(get(LOGGERS_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = DONOR_ROLE)
    void loggers_WhenDonor_ShouldReturnForbidden() throws Exception {
        mvc.perform(get(LOGGERS_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    void loggers_WhenAdminWithPassword_ShouldReturnOk() throws Exception {
        mvc.perform(get(LOGGERS_PATH).with(httpBasic(ADMIN_USER, adminPassword)))
                .andExpect(status().isOk());
    }

    @Test
    void loggers_WhenDonorWithPassword_ShouldReturnForbidden() throws Exception {
        mvc.perform(get(LOGGERS_PATH).with(httpBasic(DONOR_USER, donorPassword)))
                .andExpect(status().isForbidden());
    }

    @Test
    void loggers_WhenWrongPassword_ShouldReturnUnauthorized() throws Exception {
        mvc.perform(get(LOGGERS_PATH).with(httpBasic(ADMIN_USER, adminPassword + adminPassword)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void setLevel_WhenAnonymous_ShouldReturnUnauthorized() throws Exception {
        mvc.perform(post(LOT_LOGGER_PATH).contentType(MediaType.APPLICATION_JSON).content(levelBody("DEBUG")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = DONOR_ROLE)
    void setLevel_WhenDonor_ShouldReturnForbiddenAndKeepLevel() throws Exception {
        mvc.perform(post(LOT_LOGGER_PATH).contentType(MediaType.APPLICATION_JSON).content(levelBody("DEBUG")))
                .andExpect(status().isForbidden());

        assertThat(effectiveLevel(LOT_LOGGER)).isEqualTo(INFO_LEVEL);
    }

    @Test
    void env_WhenNotExposedAndAnonymous_ShouldRedirectToLogin() throws Exception {
        mvc.perform(get("/actuator/env"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(LOGIN_URL));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    void env_WhenNotExposedAndAdmin_ShouldReturnNotFound() throws Exception {
        mvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }

    @Test
    void businessApi_WhenAnonymous_ShouldStayProtected() throws Exception {
        mvc.perform(get("/api/v1/lots"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webPage_WhenAnonymous_ShouldRedirectToLogin() throws Exception {
        mvc.perform(get("/lots"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(LOGIN_URL));
    }
}
