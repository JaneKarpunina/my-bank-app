package ru.yandex.practicum.cash;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import ru.yandex.practicum.cash.client.NotificationClient;
import ru.yandex.practicum.cash.entity.CashOutboxMessage;
import ru.yandex.practicum.cash.repository.CashOutboxRepository;
import ru.yandex.practicum.cash.scheduler.CashOutboxScheduler;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

class CashOutboxSchedulerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CashOutboxScheduler outboxScheduler;

    @Autowired
    private CashOutboxRepository outboxRepository;

    @MockBean
    private NotificationClient notificationClient;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();

        Mockito.doNothing().when(notificationClient).sendNotification(any());
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
        assertEquals("PROCESSED", processedMessage.getStatus());
    }
}
