package ru.yandex.practicum.transfer.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.transfer.client.NotificationClient;
import ru.yandex.practicum.transfer.dto.EventEnvelope;
import ru.yandex.practicum.transfer.entity.TransferOutboxMessage;
import ru.yandex.practicum.transfer.repository.TransferOutboxRepository;
import ru.yandex.practicum.transfer.service.OutboxStatusService;

import java.time.Instant;
import java.util.List;

@Component
public class TransferOutboxScheduler {

    private final TransferOutboxRepository outboxRepository;
    private final OutboxStatusService outboxStatusService;
    private final NotificationClient notificationClient;

    public TransferOutboxScheduler(TransferOutboxRepository outboxRepository,
                                   OutboxStatusService outboxStatusService,
                                   NotificationClient notificationClient) {
        this.outboxRepository = outboxRepository;
        this.outboxStatusService = outboxStatusService;
        this.notificationClient = notificationClient;
    }

    @Scheduled(fixedDelay = 5000)
    public void processTransferOutboxMessages() {

        List<TransferOutboxMessage> pendingMessages = outboxRepository.findByStatus("PENDING");

        for (TransferOutboxMessage message : pendingMessages) {
            try {

                EventEnvelope envelope = new EventEnvelope(
                        message.getId(),
                        message.getEventType(),
                        "TRANSFER",
                        message.getPayload(),
                        Instant.now()
                );

                notificationClient.sendNotification(envelope);

                outboxStatusService.updateStatus(message.getId(), "PROCESSED", message.getAttempts());

            } catch (Exception e) {
                System.err.println("Ошибка отправки трансфер-уведомления " + message.getId() + ": " + e.getMessage());

                int currentAttempts = message.getAttempts() + 1;
                String nextStatus = (currentAttempts >= 5) ? "FAILED" : "PENDING";

                outboxStatusService.updateStatus(message.getId(), nextStatus, currentAttempts);
            }
        }
    }

}
