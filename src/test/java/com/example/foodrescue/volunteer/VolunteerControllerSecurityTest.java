package com.example.foodrescue.volunteer;

import com.example.foodrescue.infrastructure.config.JacksonConfig;
import com.example.foodrescue.security.jwt.JwtService;
import com.example.foodrescue.security.jwt.JwtValidatorService;
import com.example.foodrescue.security.error.ProblemDetailAccessDeniedHandler;
import com.example.foodrescue.security.error.ProblemDetailAuthenticationEntryPoint;
import com.example.foodrescue.security.config.SecurityConfig;
import com.example.foodrescue.volunteer.api.VolunteerController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VolunteerController.class)
@Import({
        SecurityConfig.class,
        JacksonConfig.class,
        ProblemDetailAuthenticationEntryPoint.class,
        ProblemDetailAccessDeniedHandler.class,
        VolunteerControllerSecurityTest.VolunteerServiceConfig.class
})
class VolunteerControllerSecurityTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class VolunteerServiceConfig {

        @Bean
        VolunteerService volunteerService() {
            return mock(VolunteerService.class);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private VolunteerService securedVolunteerService;

    private VolunteerService volunteerService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtValidatorService jwtValidatorService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        volunteerService = AopTestUtils.getUltimateTargetObject(securedVolunteerService);
        reset(volunteerService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void reserve_withoutToken_returnsUnauthorizedProblemDetail() throws Exception {
        UUID lotId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/lots/" + lotId + "/reservation"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/lots/" + lotId + "/reservation"));

        verifyNoInteractions(volunteerService);
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void reserve_asDonor_returnsForbiddenProblemDetail() throws Exception {
        UUID lotId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/lots/" + lotId + "/reservation"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(volunteerService);
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void reserve_asVolunteer_returnsCreated() throws Exception {
        UUID lotId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/lots/" + lotId + "/reservation"))
                .andExpect(status().isCreated());

        verify(volunteerService).reserve(lotId);
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void cancelReservation_asDonor_returnsForbiddenProblemDetail() throws Exception {
        mockMvc.perform(delete("/api/v1/lots/" + UUID.randomUUID() + "/reservation"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(volunteerService);
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void cancelReservation_asVolunteer_returnsNoContent() throws Exception {
        UUID lotId = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/lots/" + lotId + "/reservation"))
                .andExpect(status().isNoContent());

        verify(volunteerService).cancelReservation(lotId);
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void getVolunteers_asAnyAuthenticatedUser_isAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/volunteers"))
                .andExpect(status().isOk());

        verify(volunteerService).getAll();
    }
}
