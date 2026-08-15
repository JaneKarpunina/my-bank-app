package ru.yandex.practicum.transfer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import ru.yandex.practicum.transfer.client.AccountsClient;
import ru.yandex.practicum.transfer.repository.IdempotencyRepository;
import ru.yandex.practicum.transfer.repository.TransferOutboxRepository;
import ru.yandex.practicum.transfer.service.TransferService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private TransferOutboxRepository outboxRepository;

    @MockBean
    private AccountsClient accountsClient;

    @BeforeEach
    void setUp() {
        idempotencyRepository.deleteAll();
        outboxRepository.deleteAll();

        Mockito.doNothing().when(accountsClient).executeTransfer(Mockito.any(), Mockito.any());
    }

    @Test
    void shouldExecuteTransferSagaSuccessfullyAndSaveOutbox() {
        UUID idempotencyKey = UUID.randomUUID();

        transferService.executeTransfer(idempotencyKey, "ivanov", "petrov", 500);

        var savedKey = idempotencyRepository.findById(idempotencyKey).orElseThrow();
        assertEquals("SUCCESS", savedKey.getStatus());

        assertEquals(1, outboxRepository.count());
        var outboxMsg = outboxRepository.findAll().getFirst();
        assertEquals("TRANSFER_COMPLETED", outboxMsg.getEventType());
    }

    @Test
    void shouldBlockDuplicateRequestWhenIdempotencyKeyAlreadyExists() {
        UUID idempotencyKey = UUID.randomUUID();

        transferService.executeTransfer(idempotencyKey, "ivanov", "petrov", 500);
        assertEquals(1, idempotencyRepository.count());

        transferService.executeTransfer(idempotencyKey, "ivanov", "petrov", 500);

        assertEquals(1, idempotencyRepository.count());
        assertEquals(1, outboxRepository.count());
    }
}

