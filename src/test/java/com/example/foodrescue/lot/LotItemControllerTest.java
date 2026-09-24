package com.example.foodrescue.lot;

import com.example.foodrescue.common.FoodItemResponse;
import com.example.foodrescue.common.ItemUnit;
import com.example.foodrescue.common.LotNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LotItemController.class)
class LotItemControllerTest {

    private static final UUID LOT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LotItemService lotItemService;

    private FoodItemRequest validRequest() {
        return new FoodItemRequest("Морква", BigDecimal.valueOf(3), ItemUnit.KG, LocalDate.now().plusDays(3));
    }

    private FoodItemResponse itemResponse(UUID id) {
        return new FoodItemResponse(id, "Морква", BigDecimal.valueOf(3), ItemUnit.KG, LocalDate.now().plusDays(3));
    }

    @Test
    void addItem_validRequest_returnsCreatedWithLocation_andCallsService() throws Exception {
        UUID itemId = UUID.randomUUID();
        given(lotItemService.addItem(any(), any())).willReturn(itemResponse(itemId));

        mockMvc.perform(post("/api/v1/lots/" + LOT_ID + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/lots/" + LOT_ID + "/items/" + itemId));

        verify(lotItemService).addItem(any(), any());
    }

    @Test
    void addItem_invalidBody_returnsBadRequestWithErrors() throws Exception {
        FoodItemRequest invalid = new FoodItemRequest("", BigDecimal.valueOf(-1), null, null);

        mockMvc.perform(post("/api/v1/lots/" + LOT_ID + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void addItem_lotNotDraft_returnsConflict() throws Exception {
        given(lotItemService.addItem(any(), any())).willThrow(new LotNotDraftException(LOT_ID));

        mockMvc.perform(post("/api/v1/lots/" + LOT_ID + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void addItem_unknownJsonField_returnsBadRequest() throws Exception {
        String json = """
                {"unknownField": "value"}
                """;

        mockMvc.perform(post("/api/v1/lots/" + LOT_ID + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addItem_unknownLot_returnsNotFound() throws Exception {
        given(lotItemService.addItem(any(), any())).willThrow(new LotNotFoundException(LOT_ID));

        mockMvc.perform(post("/api/v1/lots/" + LOT_ID + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void findItems_returnsListOfItems() throws Exception {
        UUID itemId = UUID.randomUUID();
        given(lotItemService.findItems(LOT_ID)).willReturn(List.of(itemResponse(itemId)));

        mockMvc.perform(get("/api/v1/lots/" + LOT_ID + "/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$[0].name").value("Морква"));
    }

    @Test
    void getItem_unknownItem_returnsNotFound() throws Exception {
        UUID itemId = UUID.randomUUID();
        given(lotItemService.getItem(LOT_ID, itemId)).willThrow(new FoodItemNotFoundException(LOT_ID, itemId));

        mockMvc.perform(get("/api/v1/lots/" + LOT_ID + "/items/" + itemId))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateItem_validRequest_returnsOk_andCallsService() throws Exception {
        UUID itemId = UUID.randomUUID();
        given(lotItemService.updateItem(any(), any(), any())).willReturn(itemResponse(itemId));

        mockMvc.perform(put("/api/v1/lots/" + LOT_ID + "/items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId.toString()));

        verify(lotItemService).updateItem(any(), any(), any());
    }

    @Test
    void updateItem_lotNotDraft_returnsConflict() throws Exception {
        UUID itemId = UUID.randomUUID();
        given(lotItemService.updateItem(any(), any(), any())).willThrow(new LotNotDraftException(LOT_ID));

        mockMvc.perform(put("/api/v1/lots/" + LOT_ID + "/items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteItem_returnsNoContent_andCallsService() throws Exception {
        UUID itemId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/lots/" + LOT_ID + "/items/" + itemId))
                .andExpect(status().isNoContent());

        verify(lotItemService).deleteItem(LOT_ID, itemId);
    }

    @Test
    void deleteItem_lotNotDraft_returnsConflict() throws Exception {
        UUID itemId = UUID.randomUUID();
        doThrow(new LotNotDraftException(LOT_ID)).when(lotItemService).deleteItem(LOT_ID, itemId);

        mockMvc.perform(delete("/api/v1/lots/" + LOT_ID + "/items/" + itemId))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteItem_unknownItem_returnsNotFound() throws Exception {
        UUID itemId = UUID.randomUUID();
        doThrow(new FoodItemNotFoundException(LOT_ID, itemId)).when(lotItemService).deleteItem(LOT_ID, itemId);

        mockMvc.perform(delete("/api/v1/lots/" + LOT_ID + "/items/" + itemId))
                .andExpect(status().isNotFound());
    }
}
