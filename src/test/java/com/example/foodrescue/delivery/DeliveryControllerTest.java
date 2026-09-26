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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryController.class)
class DeliveryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private DeliveryService deliveryService;

    private DestinationPointRequest validRequest() {
        return new DestinationPointRequest(
                UUID.randomUUID(),
                "Благодійний центр",
                "вул. Центральна, 10",
                "09:00-18:00",
                Set.of(FoodCategory.BAKERY));
    }

    private DestinationPointResponse response(UUID id, DestinationPointRequest request) {
        return new DestinationPointResponse(
                id,
                request.organizationId(),
                request.name(),
                request.address(),
                request.workingHours(),
                request.acceptedCategories());
    }

    @Test
    void createDestinationPoint_validRequest_returnsCreated() throws Exception {
        DestinationPointRequest request = validRequest();
        UUID id = UUID.randomUUID();
        given(deliveryService.createDestinationPoint(any())).willReturn(response(id, request));

        mockMvc.perform(post("/api/v1/destination-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/destination-points/" + id));
    }

    @Test
    void createDestinationPoint_invalidBody_returnsBadRequestWithErrors() throws Exception {
        DestinationPointRequest invalid = new DestinationPointRequest(null, "А", "", "9-18", Set.of());

        mockMvc.perform(post("/api/v1/destination-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void updateDestinationPoint_businessException_returnsUnprocessableEntity() throws Exception {
        UUID id = UUID.randomUUID();
        DestinationPointRequest request = validRequest();
        given(deliveryService.updateDestinationPoint(eq(id), any()))
                .willThrow(new BusinessRuleException("Помилка правила"));

        mockMvc.perform(put("/api/v1/destination-points/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

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

    @Test
    void getDestinationPoints_returnsList() throws Exception {
        DestinationPointRequest request = validRequest();
        UUID id = UUID.randomUUID();
        given(deliveryService.getDestinationPoints()).willReturn(List.of(response(id, request)));

        mockMvc.perform(get("/api/v1/destination-points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()));
    }

    @Test
    void deleteDestinationPoint_returnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/destination-points/" + id))
                .andExpect(status().isNoContent());

        verify(deliveryService).deleteDestinationPoint(id);
    }
}
