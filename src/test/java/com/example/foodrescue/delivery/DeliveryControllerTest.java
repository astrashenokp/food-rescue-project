package com.example.foodrescue.delivery;

import com.example.foodrescue.common.BusinessRuleException;
import com.example.foodrescue.common.FoodCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryController.class)
class DeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeliveryService deliveryService;

    private DestinationPointRequest validRequest() {
        return new DestinationPointRequest(
                UUID.randomUUID(),
                "Благодійний центр",
                "вул. Центральна, 10",
                "09:00-18:00",
                Set.of(FoodCategory.BAKERY));
    }

    // 1. Валідний запит → правильний статус і виклик сервісу
    @Test
    void createDestinationPoint_validRequest_returnsCreated_andCallsService() throws Exception {
        DestinationPointRequest request = validRequest();
        DestinationPoint point = new DestinationPoint(
                UUID.randomUUID(),
                request.organizationId(),
                request.name(),
                request.address(),
                request.workingHours(),
                request.acceptedCategories());

        given(deliveryService.createDestinationPoint(any())).willReturn(point);

        mockMvc.perform(post("/api/v1/destination-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));

        verify(deliveryService).createDestinationPoint(any());
    }

    // 2. Невалідне тіло → 400, у відповіді errors
    @Test
    void createDestinationPoint_invalidBody_returnsBadRequestWithErrors() throws Exception {
        DestinationPointRequest invalid = new DestinationPointRequest(
                null,
                "А",
                "",
                "9-18",
                Set.of());

        mockMvc.perform(post("/api/v1/destination-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    // 3. Сервіс кидає бізнес-виняток → правильний статус
    @Test
    void deliver_serviceThrowsBusinessRule_returnsUnprocessableEntity() throws Exception {
        UUID lotId = UUID.randomUUID();
        DeliveryRequest request = new DeliveryRequest(UUID.randomUUID());

        given(deliveryService.deliver(eq(lotId), any()))
                .willThrow(new BusinessRuleException("Пункт призначення не приймає категорію лоту"));

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/delivery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    // 4. Невідоме поле в JSON → 400
    @Test
    void createDestinationPoint_unknownJsonField_returnsBadRequest() throws Exception {
        String json = """
                {
                  "organizationId": "00000000-0000-0000-0000-00000000d001",
                  "name": "Благодійний центр",
                  "address": "вул. Центральна, 10",
                  "workingHours": "09:00-18:00",
                  "acceptedCategories": ["BAKERY"],
                  "unknownField": "value"
                }
                """;

        mockMvc.perform(post("/api/v1/destination-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
