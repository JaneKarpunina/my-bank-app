package ru.yandex.practicum.cash;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.cash.client.AccountsClient;
import ru.yandex.practicum.cash.dto.CashAction;
import ru.yandex.practicum.cash.dto.CashRequest;
import ru.yandex.practicum.cash.entity.IdempotencyKey;
import ru.yandex.practicum.cash.repository.CashOutboxRepository;
import ru.yandex.practicum.cash.repository.IdempotencyRepository;
import ru.yandex.practicum.cash.service.CashService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class CashServiceRecoveryTest {

    @Autowired
    private CashService cashService;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private CashOutboxRepository outboxRepository;

    @MockBean
    private AccountsClient accountsClient;

    @BeforeEach
    public void cleanUp() {
        outboxRepository.deleteAll();
        idempotencyRepository.deleteAll();
    }

    @Test
    public void executeCash_WhenPreviousStatusIsSystemError_ShouldRecoverAndCreateSingleOutbox() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        CashRequest request = new CashRequest("recovery_user", 1000, CashAction.PUT);

        IdempotencyKey brokenKey = new IdempotencyKey();
        brokenKey.setId(idempotencyKey);
        brokenKey.setStatus("SYSTEM_ERROR");
        idempotencyRepository.save(brokenKey);

        doNothing().when(accountsClient).executeCash(eq(idempotencyKey), any());

        assertDoesNotThrow(() -> cashService.executeCash(idempotencyKey, request));

        IdempotencyKey updatedKey = idempotencyRepository.findById(idempotencyKey).orElseThrow();
        assertEquals("SUCCESS", updatedKey.getStatus());

        long outboxCount = outboxRepository.count();
        assertEquals(1, outboxCount, "Должно быть сгенерировано ровно ОДНО Outbox-сообщение!");

        verify(accountsClient, times(1)).executeCash(eq(idempotencyKey), any());
    }
}

