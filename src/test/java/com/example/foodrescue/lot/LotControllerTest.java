package com.example.foodrescue.lot;

import com.example.foodrescue.common.ConflictException;
import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.FoodItemResponse;
import com.example.foodrescue.common.LotNotFoundException;
import com.example.foodrescue.common.LotResponse;
import com.example.foodrescue.common.LotStatus;
import com.example.foodrescue.common.ItemUnit;
import com.example.foodrescue.common.StorageCondition;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private LotResponse lotResponse(UUID id, LotStatus status) {
        Instant from = Instant.now().plus(2, ChronoUnit.HOURS);
        return new LotResponse(
                id,
                UUID.randomUUID(),
                "Хліб і випічка",
                FoodCategory.BAKERY,
                List.of(new FoodItemResponse(UUID.randomUUID(), "Хліб", BigDecimal.valueOf(5), ItemUnit.KG, null)),
                BigDecimal.valueOf(5),
                StorageCondition.ROOM,
                "вул. Хлібна, 1",
                from,
                from.plus(2, ChronoUnit.HOURS),
                status,
                Instant.now(),
                null,
                null,
                null);
    }

    // 1. Валідний запит -> правильний статус і виклик сервісу
    @Test
    void validRequest_returnsCreated_andCallsService() throws Exception {
        given(lotService.create(any())).willReturn(lotResponse(UUID.randomUUID(), LotStatus.DRAFT));

        mockMvc.perform(post("/api/v1/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());

        verify(lotService).create(any());
    }

    // 2. Невалідне тіло -> 400, у відповіді errors
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

    // 3. Сервіс кидає бізнес-виняток -> правильний статус (409 при публікації)
    @Test
    void serviceThrowsConflict_onPublication_returnsConflictStatus() throws Exception {
        UUID lotId = UUID.randomUUID();
        given(lotService.publish(lotId)).willThrow(new ConflictException("Лот не в чернетці чи на модерації"));

        mockMvc.perform(post("/api/v1/lots/" + lotId + "/publication"))
                .andExpect(status().isConflict());
    }

    // 4. Невідоме поле в JSON -> 400
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

    @Test
    void findAll_withStatus_passesStatusToService() throws Exception {
        UUID lotId = UUID.randomUUID();
        given(lotService.findAll(LotStatus.PUBLISHED, null, null))
                .willReturn(List.of(lotResponse(lotId, LotStatus.PUBLISHED)));

        mockMvc.perform(get("/api/v1/lots").param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(lotId.toString()))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].items[0].name").value("Хліб"));

        verify(lotService).findAll(LotStatus.PUBLISHED, null, null);
    }

    @Test
    void findAll_unknownStatus_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/lots").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_unknownLot_returnsNotFound() throws Exception {
        UUID lotId = UUID.randomUUID();
        given(lotService.getById(lotId)).willThrow(new LotNotFoundException(lotId));

        mockMvc.perform(get("/api/v1/lots/" + lotId))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_draftLot_returnsNoContent_andCallsService() throws Exception {
        UUID lotId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/lots/" + lotId))
                .andExpect(status().isNoContent());

        verify(lotService).delete(lotId);
    }

    @Test
    void delete_lotNotDraft_returnsConflict() throws Exception {
        UUID lotId = UUID.randomUUID();
        doThrow(new LotNotDraftException(lotId)).when(lotService).delete(lotId);

        mockMvc.perform(delete("/api/v1/lots/" + lotId))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_unknownLot_returnsNotFound() throws Exception {
        UUID lotId = UUID.randomUUID();
        doThrow(new LotNotFoundException(lotId)).when(lotService).delete(lotId);

        mockMvc.perform(delete("/api/v1/lots/" + lotId))
                .andExpect(status().isNotFound());
    }
}
