package ru.yandex.practicum.cash;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.cash.service.CashService;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class CashInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CashService cashService;

    @Test
    public void processCashOperation_WithForeignAccount_ShouldReturn403Forbidden() throws Exception {

        String badJsonPayload = "{\"username\":\"petrov\",\"amount\":5000,\"action\":\"PUT\"}";
        String randomIdempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/cash")
                        .header("X-Idempotency-Key", randomIdempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJsonPayload)
                        .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "ivanov"))))

                .andExpect(status().isForbidden());
    }
}

