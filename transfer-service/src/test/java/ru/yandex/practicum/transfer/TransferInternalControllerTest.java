package ru.yandex.practicum.transfer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.transfer.service.TransferService;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class TransferInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransferService transferService;

    @Test
    public void initiateTransfer_WithForeignSender_ShouldReturn403Forbidden() throws Exception {

        String badJsonPayload = "{\"sender\":\"petrov\",\"recipient\":\"ivanov\",\"amount\":10000}";
        String randomIdempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/transfers")
                        .header("X-Idempotency-Key", randomIdempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJsonPayload)
                        .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "ivanov"))))

                .andExpect(status().isForbidden());
    }
}

