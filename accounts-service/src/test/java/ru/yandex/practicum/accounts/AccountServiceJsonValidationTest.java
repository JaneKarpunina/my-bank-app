package ru.yandex.practicum.accounts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.accounts.entity.BankAccount;
import ru.yandex.practicum.accounts.entity.OutboxMessage;
import ru.yandex.practicum.accounts.repository.AccountRepository;
import ru.yandex.practicum.accounts.repository.OutboxRepository;
import ru.yandex.practicum.accounts.service.AccountService;

import java.time.LocalDate;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
public class AccountServiceJsonValidationTest {

    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void updateClientInfo_WithSpecialCharacters_ShouldProduceValidJson() throws Exception {
        BankAccount account = new BankAccount();
        account.setUsername("json_test_user");
        account.setName("name");
        account.setBirthDate(LocalDate.now());
        account.setBalance(1000);
        accountRepository.save(account);

        String dangerousName = "Иван \"Хакер\\Тайм\" Д'Артаньян";
        UUID idempotencyKey = UUID.randomUUID();

        accountService.updateClientInfo("json_test_user", dangerousName,
                LocalDate.of(1995, 5, 5), idempotencyKey);

        OutboxMessage message = outboxRepository.findAll().stream()
                .filter(m -> "CLIENT_INFO_UPDATED".equals(m.getEventType()))
                .findFirst().orElseThrow();

        JsonNode parsedPayload = Assertions.assertDoesNotThrow(() ->
                objectMapper.readTree(message.getPayload())
        );

        Assertions.assertEquals(dangerousName, parsedPayload.get("name").asText());
    }
}

