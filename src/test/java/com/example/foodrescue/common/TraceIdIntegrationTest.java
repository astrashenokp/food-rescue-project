package com.example.foodrescue.common;

import com.example.foodrescue.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@IntegrationTest
class TraceIdIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @WithMockUser
    void request_WithTraceIdHeader_ShouldKeepIt() throws Exception {
        String header = mockMvc.perform(get("/api/v1/lots").header("X-Trace-Id", "trace-from-client"))
                .andReturn().getResponse().getHeader("X-Trace-Id");

        assertThat(header).isEqualTo("trace-from-client");
    }

    @Test
    @WithMockUser
    void request_WithoutTraceIdHeader_ShouldGenerateUuid() throws Exception {
        String header = mockMvc.perform(get("/api/v1/lots"))
                .andReturn().getResponse().getHeader("X-Trace-Id");

        assertThat(UUID.fromString(header)).isNotNull();
    }

    @Test
    void unauthorizedRequest_WithTraceIdHeader_ShouldKeepIt() throws Exception {
        var response = mockMvc.perform(get("/api/v1/lots").header("X-Trace-Id", "trace-from-client"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("trace-from-client");
    }
}
