package com.example.foodrescue.delivery.web;

import com.example.foodrescue.common.ForbiddenActionException;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.LotResponse;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.StorageCondition;
import com.example.foodrescue.delivery.DeliveryService;
import com.example.foodrescue.delivery.DeliveryResponse;
import com.example.foodrescue.delivery.DeliveryRequest;
import com.example.foodrescue.delivery.ConfirmationRequest;
import com.example.foodrescue.delivery.PickupRequest;
import com.example.foodrescue.infrastructure.config.JacksonConfig;
import com.example.foodrescue.infrastructure.config.WebConfig;
import com.example.foodrescue.infrastructure.error.GlobalExceptionHandler;
import com.example.foodrescue.infrastructure.error.WebBusinessErrorAdvice;
import com.example.foodrescue.security.config.SecurityConfig;
import com.example.foodrescue.security.error.ProblemDetailAccessDeniedHandler;
import com.example.foodrescue.security.error.ProblemDetailAuthenticationEntryPoint;
import com.example.foodrescue.security.jwt.JwtService;
import com.example.foodrescue.security.jwt.JwtValidatorService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DeliveryWebController.class)
@Import({SecurityConfig.class, WebConfig.class, JacksonConfig.class, GlobalExceptionHandler.class, WebBusinessErrorAdvice.class,
        ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class})
class DeliveryWebControllerSecurityTest {

    private static final UUID LOT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a4");

    @Autowired WebApplicationContext context;
    @MockitoBean DeliveryService deliveryService;
    @MockitoBean JwtService jwtService;
    @MockitoBean JwtValidatorService jwtValidatorService;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean PasswordEncoder passwordEncoder;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void lotInStatus(LotStatus status) {
        when(deliveryService.getLotOverview(LOT_ID)).thenReturn(new LotResponse(
                LOT_ID, UUID.randomUUID(), "Овочі", FoodCategory.VEGETABLES,
                List.of(), new BigDecimal("8.00"), StorageCondition.ROOM,
                "вул. Ринкова, 4", null, null, status,
                null, null, UUID.randomUUID(), null));
        if (status == LotStatus.PICKED_UP || status == LotStatus.DELIVERED) {
            when(deliveryService.getDelivery(LOT_ID)).thenReturn(new DeliveryResponse(
                    LOT_ID, UUID.randomUUID(), null, status, Instant.parse("2026-10-20T12:00:00Z"),
                    new BigDecimal("8.00"), null, status == LotStatus.DELIVERED ? "123456" : null,
                    null, null));
        }
    }

    @Test
    void anonymousUserGetsLoginRedirect() throws Exception {
        mvc.perform(get("/deliveries"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void coordinatorCanViewList() throws Exception {
        mvc.perform(get("/deliveries"))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery/deliveries"));
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void volunteerCanSeeReservedLotDetailsAndHistory() throws Exception {
        lotInStatus(LotStatus.RESERVED);
        mvc.perform(get("/deliveries/{lotId}", LOT_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery/details"));
        verify(deliveryService).getHistory(LOT_ID);
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void unknownLotShowsHtml404Page() throws Exception {
        when(deliveryService.getLotOverview(LOT_ID)).thenThrow(new LotNotFoundException(LOT_ID));
        mvc.perform(get("/deliveries/{lotId}", LOT_ID))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void forbiddenBusinessActionShowsHtml403Page() throws Exception {
        when(deliveryService.getLotOverview(LOT_ID))
                .thenThrow(new ForbiddenActionException("Дію заборонено"));
        mvc.perform(get("/deliveries/{lotId}", LOT_ID))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void donorCannotViewDeliveryList() throws Exception {
        mvc.perform(get("/deliveries"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void donorCannotViewDeliveryDetails() throws Exception {
        mvc.perform(get("/deliveries/{lotId}", LOT_ID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(roles = "COORDINATOR")
    void coordinatorCannotPickupEvenWithCsrf() throws Exception {
        mvc.perform(post("/deliveries/{lotId}/pickup", LOT_ID).with(csrf())
                        .param("actualWeightKg", "8.00"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(roles = "DONOR")
    void donorCannotDeliverOrConfirm() throws Exception {
        mvc.perform(post("/deliveries/{lotId}/deliver", LOT_ID).with(csrf())
                        .param("destinationPointId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/deliveries/{lotId}/confirm", LOT_ID).with(csrf())
                        .param("confirmationCode", "123456")
                        .param("receivedWeightKg", "8.00"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void pickupWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/deliveries/{lotId}/pickup", LOT_ID).param("actualWeightKg", "8.00"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(deliveryService);
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void invalidPickupShowsSameForm() throws Exception {
        lotInStatus(LotStatus.RESERVED);
        mvc.perform(post("/deliveries/{lotId}/pickup", LOT_ID).with(csrf())
                        .param("actualWeightKg", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery/details"));
        verify(deliveryService, org.mockito.Mockito.never()).pickup(any(), any());
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void validPickupUsesServiceAndRedirects() throws Exception {
        mvc.perform(post("/deliveries/{lotId}/pickup", LOT_ID).with(csrf())
                        .param("actualWeightKg", "8.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deliveries/" + LOT_ID));
        ArgumentCaptor<PickupRequest> captor = ArgumentCaptor.forClass(PickupRequest.class);
        verify(deliveryService).pickup(org.mockito.ArgumentMatchers.eq(LOT_ID), captor.capture());
        assertEquals(new BigDecimal("8.00"), captor.getValue().actualWeightKg());
    }
    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void invalidDeliveryFormShowsError() throws Exception {
        lotInStatus(LotStatus.PICKED_UP);
        mvc.perform(post("/deliveries/{lotId}/deliver", LOT_ID).with(csrf())
                        .param("destinationPointId", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery/details"));
        verify(deliveryService, org.mockito.Mockito.never()).deliver(any(), any());
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void validDeliveryUsesExistingService() throws Exception {
        UUID pointId = UUID.randomUUID();
        mvc.perform(post("/deliveries/{lotId}/deliver", LOT_ID).with(csrf())
                        .param("destinationPointId", pointId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deliveries/" + LOT_ID));
        ArgumentCaptor<DeliveryRequest> captor = ArgumentCaptor.forClass(DeliveryRequest.class);
        verify(deliveryService).deliver(org.mockito.ArgumentMatchers.eq(LOT_ID), captor.capture());
        assertEquals(pointId, captor.getValue().destinationPointId());
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void invalidConfirmationCodeShowsError() throws Exception {
        lotInStatus(LotStatus.DELIVERED);
        mvc.perform(post("/deliveries/{lotId}/confirm", LOT_ID).with(csrf())
                        .param("confirmationCode", "123")
                        .param("receivedWeightKg", "8.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery/details"));
        verify(deliveryService, org.mockito.Mockito.never()).confirm(any(), any());
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void validConfirmationUsesExistingService() throws Exception {
        mvc.perform(post("/deliveries/{lotId}/confirm", LOT_ID).with(csrf())
                        .param("confirmationCode", "123456")
                        .param("receivedWeightKg", "7.90"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/deliveries/" + LOT_ID));
        ArgumentCaptor<ConfirmationRequest> captor = ArgumentCaptor.forClass(ConfirmationRequest.class);
        verify(deliveryService).confirm(org.mockito.ArgumentMatchers.eq(LOT_ID), captor.capture());
        assertEquals("123456", captor.getValue().confirmationCode());
    }



    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void pickupOtherVolunteersLot_returnsForbiddenInsteadOfFormError() throws Exception {
        doThrow(new ForbiddenActionException("Лот зарезервовано іншим волонтером"))
                .when(deliveryService).pickup(org.mockito.ArgumentMatchers.eq(LOT_ID), any());

        mvc.perform(post("/deliveries/{lotId}/pickup", LOT_ID).with(csrf())
                        .param("actualWeightKg", "8.00"))
                .andExpect(status().isForbidden());
    }
}
