package ru.yandex.practicum.transfer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.transfer.repository.IdempotencyRepository;
import ru.yandex.practicum.transfer.repository.TransferOutboxRepository; // Ваше имя репозитория outbox
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

    @MockBean(name = "internalServicesWebClient")
    private WebClient internalServicesWebClient;

    @BeforeEach
    void setUp() {
        idempotencyRepository.deleteAll();
        outboxRepository.deleteAll();

        WebClient.RequestBodyUriSpec requestBodyUriSpec = Mockito.mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = Mockito.mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = Mockito.mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = Mockito.mock(WebClient.ResponseSpec.class);

        Mockito.when(internalServicesWebClient.post()).thenReturn(requestBodyUriSpec);
        Mockito.when(requestBodyUriSpec.uri(Mockito.anyString())).thenReturn(requestBodySpec);

        Mockito.when(requestBodySpec.header(Mockito.anyString(), Mockito.any())).thenReturn(requestBodySpec);

        Mockito.when(requestBodySpec.bodyValue(Mockito.any())).thenReturn(requestHeadersSpec);
        Mockito.when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        Mockito.when(responseSpec.toBodilessEntity()).thenReturn(Mono.empty());
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

