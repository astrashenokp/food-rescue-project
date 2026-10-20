package com.example.foodrescue.lot.web;

import com.example.foodrescue.common.JacksonConfig;
import com.example.foodrescue.common.JwtService;
import com.example.foodrescue.common.JwtValidatorService;
import com.example.foodrescue.common.ProblemDetailAccessDeniedHandler;
import com.example.foodrescue.common.ProblemDetailAuthenticationEntryPoint;
import com.example.foodrescue.common.SecurityConfig;
import com.example.foodrescue.common.WebConfig;
import com.example.foodrescue.lot.LotService;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(LotWebController.class)
@Import({
        SecurityConfig.class,
        WebConfig.class,
        JacksonConfig.class,
        ProblemDetailAuthenticationEntryPoint.class,
        ProblemDetailAccessDeniedHandler.class
})
class LotWebControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private LotService lotService;

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
        mvc.perform(get("/lots")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/lots").param("title", "abc")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void invalidFormStaysOnForm() throws Exception {
        mvc.perform(post("/lots").with(csrf()).param("title", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("lot/create-form"));
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void validFormRedirectsToList() throws Exception {
        mvc.perform(post("/lots").with(csrf())
                        .param("title", "Bread")
                        .param("category", "BAKERY")
                        .param("itemName", "Bread")
                        .param("itemQuantity", "5")
                        .param("itemUnit", "KG")
                        .param("totalWeightKg", "5")
                        .param("storageCondition", "ROOM")
                        .param("pickupAddress", "Kyiv")
                        .param("pickupFrom", "2030-01-01T10:00")
                        .param("pickupTo", "2030-01-01T12:00"))
                .andExpect(redirectedUrl("/lots"));
    }
}
