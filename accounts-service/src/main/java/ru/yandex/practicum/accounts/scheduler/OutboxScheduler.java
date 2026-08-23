package ru.yandex.practicum.accounts.scheduler;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.accounts.client.NotificationClient;
import ru.yandex.practicum.accounts.dto.EventEnvelope;
import ru.yandex.practicum.accounts.entity.OutboxMessage;
import ru.yandex.practicum.accounts.repository.OutboxRepository;
import ru.yandex.practicum.accounts.service.OutboxStatusService;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final OutboxStatusService outboxStatusService;
    private final NotificationClient notificationClient;

    public OutboxScheduler(
            OutboxRepository outboxRepository,
            OutboxStatusService outboxStatusService,
            NotificationClient notificationClient) {

        this.outboxRepository = outboxRepository;
        this.outboxStatusService = outboxStatusService;
        this.notificationClient = notificationClient;
    }


    @Scheduled(fixedDelay = 5000)
    public void processOutboxMessages() {
        List<OutboxMessage> pendingMessages = outboxRepository.findByStatus("PENDING");

        for (OutboxMessage message : pendingMessages) {
            try {
                EventEnvelope envelope = new EventEnvelope(
                        message.getId(),
                        message.getEventType(),
                        message.getAggregateType(),
                        message.getPayload(),
                        Instant.now()
                );


                notificationClient.sendNotification(envelope);

                outboxStatusService.updateStatus(message.getId(), "PROCESSED", message.getAttempts());

            } catch (Exception e) {
                System.err.println("Ошибка отправки outbox сообщения " + message.getId() + ": " + e.getMessage());

                int currentAttempts = message.getAttempts() + 1;
                String nextStatus = (currentAttempts >= 5) ? "FAILED" : "PENDING";

                outboxStatusService.updateStatus(message.getId(), nextStatus, currentAttempts);
            }
        }
    }


}
