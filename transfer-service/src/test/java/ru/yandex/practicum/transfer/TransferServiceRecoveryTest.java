package ru.yandex.practicum.transfer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import ru.yandex.practicum.transfer.client.AccountsClient;
import ru.yandex.practicum.transfer.dto.TransferRequest;
import ru.yandex.practicum.transfer.entity.IdempotencyKey;
import ru.yandex.practicum.transfer.repository.IdempotencyRepository;
import ru.yandex.practicum.transfer.repository.TransferOutboxRepository;
import ru.yandex.practicum.transfer.service.TransferService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class TransferServiceRecoveryTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private TransferOutboxRepository outboxRepository;

    @MockBean
    private AccountsClient accountsClient;

    @BeforeEach
    public void cleanUp() {
        outboxRepository.deleteAll();
        idempotencyRepository.deleteAll();
    }

    @Test
    public void executeTransfer_WhenPreviousStatusIsSystemError_ShouldRecoverAndComplete() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        TransferRequest request = new TransferRequest("sender_user", "recipient_user", 2500);

        IdempotencyKey brokenKey = new IdempotencyKey();
        brokenKey.setId(idempotencyKey);
        brokenKey.setStatus("SYSTEM_ERROR");
        idempotencyRepository.save(brokenKey);

        doNothing().when(accountsClient).executeTransfer(eq(idempotencyKey), any());

        Assertions.assertDoesNotThrow(() ->
                transferService.executeTransfer(idempotencyKey, request.sender(),
                        request.recipient(), request.amount())
        );

        IdempotencyKey updatedKey = idempotencyRepository.findById(idempotencyKey).orElseThrow();
        Assertions.assertEquals("SUCCESS", updatedKey.getStatus());

        Assertions.assertEquals(1, outboxRepository.count(), "Запись Outbox перевода" +
                " должна быть создана в единственном экземпляре!");

        verify(accountsClient, times(1)).executeTransfer(eq(idempotencyKey), any());
    }
}

