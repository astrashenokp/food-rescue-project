package com.example.foodrescue.delivery.web;

import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.infrastructure.config.JacksonConfig;
import com.example.foodrescue.security.jwt.JwtService;
import com.example.foodrescue.security.jwt.JwtValidatorService;
import com.example.foodrescue.security.error.ProblemDetailAccessDeniedHandler;
import com.example.foodrescue.security.error.ProblemDetailAuthenticationEntryPoint;
import com.example.foodrescue.security.config.SecurityConfig;
import com.example.foodrescue.infrastructure.config.WebConfig;
import com.example.foodrescue.delivery.DeliveryService;
import com.example.foodrescue.delivery.DestinationPointRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DestinationPointWebController.class)
@Import({
        SecurityConfig.class,
        WebConfig.class,
        JacksonConfig.class,
        ProblemDetailAuthenticationEntryPoint.class,
        ProblemDetailAccessDeniedHandler.class
})
class DestinationPointWebControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private DeliveryService deliveryService;

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
        mvc.perform(get("/destination-points"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void otherRoleCannotOpenPoints() throws Exception {
        mvc.perform(get("/destination-points"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void otherRoleCannotCreateEvenWithCsrf() throws Exception {
        mvc.perform(post("/destination-points").with(csrf())
                        .param("organizationId", "00000000-0000-0000-0000-000000000001")
                        .param("name", "Їдальня")
                        .param("address", "Київ")
                        .param("workingHours", "09:00-18:00")
                        .param("acceptedCategories", "BAKERY"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void coordinatorCanOpenListAndForm() throws Exception {
        mvc.perform(get("/destination-points"))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery/list"));
        mvc.perform(get("/destination-points/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery/create-form"));
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/destination-points").param("name", "Їдальня"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void invalidFormStaysOnForm() throws Exception {
        mvc.perform(post("/destination-points").with(csrf())
                        .param("name", "")
                        .param("address", "")
                        .param("workingHours", "неправильний час"))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery/create-form"));
        verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void validFormCreatesPointAndRedirectsToList() throws Exception {
        UUID organizationId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        mvc.perform(post("/destination-points").with(csrf())
                        .param("organizationId", organizationId.toString())
                        .param("name", "Їдальня")
                        .param("address", "Київ, вул. Хлібна, 1")
                        .param("workingHours", "09:00-18:00")
                        .param("acceptedCategories", "BAKERY", "VEGETABLES"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/destination-points"));

        ArgumentCaptor<DestinationPointRequest> request = ArgumentCaptor.forClass(DestinationPointRequest.class);
        verify(deliveryService).createDestinationPoint(request.capture());
        assertEquals(organizationId, request.getValue().organizationId());
        assertEquals(Set.of(FoodCategory.BAKERY, FoodCategory.VEGETABLES), request.getValue().acceptedCategories());
    }
}
