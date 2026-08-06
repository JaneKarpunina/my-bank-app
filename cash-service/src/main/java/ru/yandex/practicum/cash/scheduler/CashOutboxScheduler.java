package ru.yandex.practicum.cash.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.cash.dto.EventEnvelope;
import ru.yandex.practicum.cash.entity.CashOutboxMessage;
import ru.yandex.practicum.cash.repository.CashOutboxRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class CashOutboxScheduler {

    private final CashOutboxRepository outboxRepository;
    private final WebClient internalServicesWebClient;
    private final String notificationServiceUrl;

    public CashOutboxScheduler(CashOutboxRepository outboxRepository,
                                   WebClient internalServicesWebClient,
                                   @Value("${app.services.notification-url}") String notificationServiceUrl) {
        this.outboxRepository = outboxRepository;
        this.internalServicesWebClient = internalServicesWebClient;
        this.notificationServiceUrl = notificationServiceUrl;
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

                internalServicesWebClient.post()
                        .uri(notificationServiceUrl + "/notifications/events")
                        .header("Content-Type", "application/json")
                        .bodyValue(envelope)
                        .retrieve()
                        .toBodilessEntity()
                        .block();

                updateMessageStatus(message.getId(), "PROCESSED", message.getAttempts());

            } catch (Exception e) {

                int currentAttempts = message.getAttempts() + 1;
                String nextStatus = (currentAttempts >= 5) ? "FAILED" : "PENDING";

                updateMessageStatus(message.getId(), nextStatus, currentAttempts);
            }
        }
    }


    @Transactional
    public void updateMessageStatus(UUID id, String status, int attempts) {
        outboxRepository.findById(id).ifPresent(msg -> {
            msg.setStatus(status);
            msg.setAttempts(attempts);
            outboxRepository.save(msg);
        });
    }

}

