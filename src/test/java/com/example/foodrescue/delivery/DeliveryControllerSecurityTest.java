package com.example.foodrescue.delivery;

import com.example.foodrescue.common.ForbiddenActionException;
import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.infrastructure.config.JacksonConfig;
import com.example.foodrescue.infrastructure.error.GlobalExceptionHandler;
import com.example.foodrescue.security.jwt.JwtService;
import com.example.foodrescue.security.jwt.JwtValidatorService;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.security.error.ProblemDetailAccessDeniedHandler;
import com.example.foodrescue.security.error.ProblemDetailAuthenticationEntryPoint;
import com.example.foodrescue.security.config.SecurityConfig;
import com.example.foodrescue.delivery.api.DeliveryController;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryController.class)
@Import({
        SecurityConfig.class,
        JacksonConfig.class,
        GlobalExceptionHandler.class,
        ProblemDetailAuthenticationEntryPoint.class,
        ProblemDetailAccessDeniedHandler.class,
        DeliveryControllerSecurityTest.DeliveryServiceConfig.class
})
class DeliveryControllerSecurityTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class DeliveryServiceConfig {
        @Bean
        DeliveryService deliveryService() {
            return mock(DeliveryService.class);
        }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DeliveryService deliveryService;

    @MockitoBean private JwtService jwtService;
    @MockitoBean private JwtValidatorService jwtValidatorService;
    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private DeliveryService deliveryServiceMock;

    @BeforeEach
    void setUp() throws Exception {
        deliveryServiceMock = AopTestUtils.getUltimateTargetObject(deliveryService);
        reset(deliveryServiceMock);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private DestinationPointRequest pointRequest() {
        return new DestinationPointRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "Благодійний центр", "вул. Центральна, 10", "09:00-18:00", Set.of(FoodCategory.BAKERY));
    }

    private DestinationPointResponse pointResponse(UUID id) {
        DestinationPointRequest request = pointRequest();
        return new DestinationPointResponse(id, request.organizationId(), request.name(),
                request.address(), request.workingHours(), request.acceptedCategories());
    }

    private DeliveryResponse deliveryResponse(UUID lotId) {
        return new DeliveryResponse(
                lotId, UUID.randomUUID(), null, LotStatus.PICKED_UP, Instant.parse("2026-10-13T12:00:00Z"),
                new BigDecimal("3.5"), null, null, null, null);
    }

    @Test
    void withoutToken_readingDestinations_returnsUnauthorizedProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/destination-points"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/destination-points"));

        verifyNoInteractions(deliveryServiceMock);
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void donor_creatingDestination_returnsForbiddenProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/destination-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pointRequest())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(deliveryServiceMock);
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void coordinator_creatingDestination_returnsCreated() throws Exception {
        UUID pointId = UUID.randomUUID();
        given(deliveryServiceMock.createDestinationPoint(any())).willReturn(pointResponse(pointId));

        mockMvc.perform(post("/api/v1/destination-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pointRequest())))
                .andExpect(status().isCreated());

        verify(deliveryServiceMock).createDestinationPoint(any());
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void coordinator_pickingUpLot_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/lots/" + UUID.randomUUID() + "/pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PickupRequest(new BigDecimal("3.5")))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(deliveryServiceMock);
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void volunteer_pickingUpLot_returnsOk() throws Exception {
        UUID lotId = UUID.randomUUID();
        given(deliveryServiceMock.pickup(eq(lotId), any())).willReturn(deliveryResponse(lotId));

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PickupRequest(new BigDecimal("3.5")))))
                .andExpect(status().isOk());

        verify(deliveryServiceMock).pickup(eq(lotId), any());
    }


    @Test
    @WithMockUser(roles = "DONOR")
    void donor_cannotSeeDeliveriesEvenWhenLoggedIn() throws Exception {
        mockMvc.perform(get("/api/v1/deliveries"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/deliveries/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(deliveryServiceMock);
    }


    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void volunteer_pickingUpSomeoneElsesLot_returnsForbiddenProblemDetail() throws Exception {
        UUID lotId = UUID.randomUUID();
        doThrow(new ForbiddenActionException("Лот зарезервовано іншим волонтером"))
                .when(deliveryServiceMock).pickup(eq(lotId), any());

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PickupRequest(new BigDecimal("3.5")))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
