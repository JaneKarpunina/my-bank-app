package ru.yandex.practicum.cash;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
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

    @MockBean(name = "internalServicesWebClient")
    private WebClient internalServicesWebClient;

    @BeforeEach
    void setUp() {
        idempotencyRepository.deleteAll();
        outboxRepository.deleteAll();

        WebClient.RequestBodyUriSpec requestBodyUriSpec = Mockito.mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = Mockito.mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = Mockito.mock(WebClient.RequestHeadersSpec.class); // 🌟 ДОБАВЛЕНО
        WebClient.ResponseSpec responseSpec = Mockito.mock(WebClient.ResponseSpec.class);

        Mockito.when(internalServicesWebClient.post()).thenReturn(requestBodyUriSpec);
        Mockito.when(requestBodyUriSpec.uri(Mockito.anyString())).thenReturn(requestBodySpec);

        Mockito.when(requestBodySpec.bodyValue(Mockito.any())).thenReturn(requestHeadersSpec);

        Mockito.when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        Mockito.when(responseSpec.toBodilessEntity()).thenReturn(Mono.empty());
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
