package ru.yandex.practicum.cash.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.cash.client.NotificationClient;
import ru.yandex.practicum.cash.dto.EventEnvelope;
import ru.yandex.practicum.cash.entity.CashOutboxMessage;
import ru.yandex.practicum.cash.repository.CashOutboxRepository;
import ru.yandex.practicum.cash.service.OutboxStatusService;

import java.time.Instant;
import java.util.List;

@Component
public class CashOutboxScheduler {

    private final CashOutboxRepository outboxRepository;
    private final OutboxStatusService outboxStatusService;
    private final NotificationClient notificationClient;

    public CashOutboxScheduler(CashOutboxRepository outboxRepository,
                               OutboxStatusService outboxStatusService, NotificationClient notificationClient) {
        this.outboxRepository = outboxRepository;
        this.outboxStatusService = outboxStatusService;
        this.notificationClient = notificationClient;
    }

    @Scheduled(fixedDelay = 5000)
    public void processCashOutboxMessages() {

        List<CashOutboxMessage> pendingMessages = outboxRepository.findByStatus("PENDING");

        for (CashOutboxMessage message : pendingMessages) {
            try {

                EventEnvelope envelope = new EventEnvelope(
                        message.getId(),
                        message.getEventType(),
                        "CASH",
                        message.getPayload(),
                        Instant.now()
                );

                notificationClient.sendNotification(envelope);

                outboxStatusService.updateStatus(message.getId(), "PROCESSED", message.getAttempts());

            } catch (Exception e) {

                int currentAttempts = message.getAttempts() + 1;
                String nextStatus = (currentAttempts >= 5) ? "FAILED" : "PENDING";

                outboxStatusService.updateStatus(message.getId(), nextStatus, currentAttempts);
            }
        }
    }

}

