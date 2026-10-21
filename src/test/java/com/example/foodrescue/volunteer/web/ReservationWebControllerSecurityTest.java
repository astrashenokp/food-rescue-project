package com.example.foodrescue.volunteer.web;

import com.example.foodrescue.infrastructure.config.JacksonConfig;
import com.example.foodrescue.security.jwt.JwtService;
import com.example.foodrescue.security.jwt.JwtValidatorService;
import com.example.foodrescue.security.error.ProblemDetailAccessDeniedHandler;
import com.example.foodrescue.security.error.ProblemDetailAuthenticationEntryPoint;
import com.example.foodrescue.security.config.SecurityConfig;
import com.example.foodrescue.infrastructure.config.WebConfig;
import com.example.foodrescue.volunteer.VolunteerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ReservationWebController.class)
@Import({
        SecurityConfig.class,
        WebConfig.class,
        JacksonConfig.class,
        ProblemDetailAuthenticationEntryPoint.class,
        ProblemDetailAccessDeniedHandler.class
})
class ReservationWebControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private VolunteerService volunteerService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtValidatorService jwtValidatorService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/reservations")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/reservations").param("lotId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void invalidFormStaysOnPage() throws Exception {
        mvc.perform(post("/reservations").with(csrf()).param("lotId", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("volunteer/reservations"));
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void validFormRedirectsToList() throws Exception {
        mvc.perform(post("/reservations").with(csrf())
                        .param("lotId", UUID.randomUUID().toString()))
                .andExpect(redirectedUrl("/reservations"));
    }
}
