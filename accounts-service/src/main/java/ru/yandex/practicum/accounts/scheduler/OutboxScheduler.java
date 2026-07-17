package ru.yandex.practicum.accounts.scheduler;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.accounts.dto.EventEnvelope;
import ru.yandex.practicum.accounts.entity.OutboxMessage;
import ru.yandex.practicum.accounts.repository.OutboxRepository;

import java.time.Instant;
import java.util.List;

import static org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient;

@Component
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final WebClient webClient;

    public OutboxScheduler(
            OutboxRepository outboxRepository,
            @Qualifier("notificationWebClient") WebClient webClient) {

        this.outboxRepository = outboxRepository;
        this.webClient = webClient;
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


                webClient.post()
                        .uri("/notifications/events")
                        .header("Content-Type", "application/json")
                        .bodyValue(envelope)
                        .retrieve()
                        .toBodilessEntity()
                        .block();

                updateMessageStatus(message.getId(), "PROCESSED", message.getAttempts());

            } catch (Exception e) {
                System.err.println("Ошибка отправки outbox сообщения " + message.getId() + ": " + e.getMessage());

                int currentAttempts = message.getAttempts() + 1;
                String nextStatus = (currentAttempts >= 5) ? "FAILED" : "PENDING";

                updateMessageStatus(message.getId(), nextStatus, currentAttempts);
            }
        }
    }

    @Transactional
    public void updateMessageStatus(java.util.UUID id, String status, int attempts) {
        outboxRepository.findById(id).ifPresent(msg -> {
            msg.setStatus(status);
            msg.setAttempts(attempts);
            outboxRepository.save(msg);
        });
    }


}
