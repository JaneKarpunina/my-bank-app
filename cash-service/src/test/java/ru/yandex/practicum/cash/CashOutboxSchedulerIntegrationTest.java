package ru.yandex.practicum.cash;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.cash.entity.CashOutboxMessage;
import ru.yandex.practicum.cash.repository.CashOutboxRepository;
import ru.yandex.practicum.cash.scheduler.CashOutboxScheduler;
import java.util.UUID;

class CashOutboxSchedulerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CashOutboxScheduler outboxScheduler;

    @Autowired
    private CashOutboxRepository outboxRepository;

    @MockBean(name = "internalServicesWebClient")
    private WebClient internalServicesWebClient;

    @BeforeEach
    void setUp() {
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
    void shouldSendPendingMessagesToNotificationService() {
        CashOutboxMessage message = new CashOutboxMessage();
        message.setId(UUID.randomUUID());
        message.setEventType("CASH_OPERATION_COMPLETED");
        message.setPayload("{\"username\":\"ivanov\",\"amount\":1000,\"cashAction\":\"PUT\"}");
        message.setStatus("PENDING");
        message.setAttempts(0);
        outboxRepository.save(message);

        outboxScheduler.processCashOutboxMessages();

        CashOutboxMessage processedMessage = outboxRepository.findById(message.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("PROCESSED", processedMessage.getStatus());
    }
}
