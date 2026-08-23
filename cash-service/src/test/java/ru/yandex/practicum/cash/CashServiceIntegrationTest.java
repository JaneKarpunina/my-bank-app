package ru.yandex.practicum.cash;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import ru.yandex.practicum.cash.client.AccountsClient;
import ru.yandex.practicum.cash.dto.CashAction;
import ru.yandex.practicum.cash.dto.CashRequest;
import ru.yandex.practicum.cash.repository.CashOutboxRepository;
import ru.yandex.practicum.cash.repository.IdempotencyRepository;
import ru.yandex.practicum.cash.service.CashService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CashServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CashService cashService;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private CashOutboxRepository outboxRepository;

    @MockBean
    private AccountsClient accountsClient;

    @BeforeEach
    void setUp() {
        idempotencyRepository.deleteAll();
        outboxRepository.deleteAll();

        Mockito.doNothing().when(accountsClient).executeCash(Mockito.any(), Mockito.any());
    }

    @Test
    void shouldExecuteCashSagaThroughRealPostgresContainer() {
        UUID idempotencyKey = UUID.randomUUID();
        CashRequest request = new CashRequest("ivanov", 1000, CashAction.PUT);

        cashService.executeCash(idempotencyKey, request);

        assertEquals(1, outboxRepository.count());
        assertEquals(1, idempotencyRepository.count());
    }
}
