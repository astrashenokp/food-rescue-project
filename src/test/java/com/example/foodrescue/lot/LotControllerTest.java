package com.example.foodrescue.lot;

import com.example.foodrescue.common.error.ConflictException;
import com.example.foodrescue.common.lot.FoodCategory;
import com.example.foodrescue.common.lot.FoodLot;
import com.example.foodrescue.common.lot.ItemUnit;
import com.example.foodrescue.common.lot.StorageCondition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LotController.class)
class LotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LotService lotService;

    private LotRequest validRequest() {
        Instant from = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant to = from.plus(2, ChronoUnit.HOURS);
        return new LotRequest(
                UUID.randomUUID(),
                "Хліб і випічка",
                FoodCategory.BAKERY,
                List.of(new FoodItemRequest("Хліб", BigDecimal.valueOf(5), ItemUnit.KG, null)),
                BigDecimal.valueOf(5),
                StorageCondition.ROOM,
                "вул. Хлібна, 1",
                from,
                to);
    }

    // 1. Валідний запит → правильний статус і виклик сервісу
    @Test
    void validRequest_returnsCreated_andCallsService() throws Exception {
        FoodLot lot = new FoodLot();
        lot.setId(UUID.randomUUID());
        given(lotService.create(any())).willReturn(lot);

        mockMvc.perform(post("/api/v1/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());

        verify(lotService).create(any());
    }

    // 2. Невалідне тіло → 400, у відповіді errors
    @Test
    void invalidBody_returnsBadRequestWithErrors() throws Exception {
        LotRequest invalid = new LotRequest(
                null, "Хл", null, List.of(), null, null, "", null, null);

        mockMvc.perform(post("/api/v1/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    // 3. Сервіс кидає бізнес-виняток → правильний статус (409 при публікації)
    @Test
    void serviceThrowsConflict_onPublication_returnsConflictStatus() throws Exception {
        UUID lotId = UUID.randomUUID();
        given(lotService.publish(lotId)).willThrow(new ConflictException("Лот не в чернетці чи на модерації"));

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/publication"))
                .andExpect(status().isConflict());
    }

    // 4. Невідоме поле в JSON → 400
    @Test
    void unknownJsonField_returnsBadRequest() throws Exception {
        String json = """
                {"unknownField": "value"}
                """;

        mockMvc.perform(post("/api/v1/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
