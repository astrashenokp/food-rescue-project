# Шаблон тесту контролера

Копіюєш під свій контролер (див. §11 контракту), заміняєш `Lot` на свою сутність і підставляєш реальні поля запиту/відповіді. Мокаєш лише **свій** сервіс — чужий код для тесту не потрібен.

```java
package com.example.foodrescue.lot;

import com.example.foodrescue.common.error.ConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LotController.class)
class LotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LotService lotService;

    // 1. Валідний запит → правильний статус і виклик сервісу
    @Test
    void validRequest_returnsCreated_andCallsService() throws Exception {
        var request = new LotRequest(/* валідні поля */);

        mockMvc.perform(post("/api/v1/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(lotService).create(any());
    }

    // 2. Невалідне тіло → 400 з errors
    @Test
    void invalidBody_returnsBadRequestWithErrors() throws Exception {
        var request = new LotRequest(/* поле, що порушує @NotBlank/@Size/... */);

        mockMvc.perform(post("/api/v1/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    // 3. Сервіс кидає бізнес-виняток → правильний статус (403/404/409/422)
    @Test
    void serviceThrowsBusinessException_returnsMappedStatus() throws Exception {
        var request = new LotRequest(/* валідні поля */);
        given(lotService.create(any())).willThrow(new ConflictException("Лот у стані, що не дозволяє дію"));

        mockMvc.perform(post("/api/v1/lots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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
```

Не забудь `import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;` для другого тесту.
