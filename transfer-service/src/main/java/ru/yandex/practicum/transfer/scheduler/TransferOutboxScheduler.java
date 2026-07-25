package ru.yandex.practicum.transfer.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.transfer.entity.TransferOutboxMessage;
import ru.yandex.practicum.transfer.repository.TransferOutboxRepository;

import java.util.List;
import java.util.UUID;

@Component
public class TransferOutboxScheduler {

    private final TransferOutboxRepository outboxRepository;
    private final WebClient internalServicesWebClient;
    private final String notificationServiceUrl;

    public TransferOutboxScheduler(TransferOutboxRepository outboxRepository,
                                   WebClient internalServicesWebClient,
                                   @Value("${app.services.notification-url}") String notificationServiceUrl) {
        this.outboxRepository = outboxRepository;
        this.internalServicesWebClient = internalServicesWebClient;
        this.notificationServiceUrl = notificationServiceUrl;
    }

    @Scheduled(fixedDelay = 5000)
    public void processTransferOutboxMessages() {

        List<TransferOutboxMessage> pendingMessages = outboxRepository.findByStatus("PENDING");

        for (TransferOutboxMessage message : pendingMessages) {
            try {

                internalServicesWebClient.post()
                        .uri(notificationServiceUrl + "/notifications/events")
                        .header("Content-Type", "application/json")
                        .bodyValue(message.getPayload())
                        .retrieve()
                        .toBodilessEntity()
                        .block();

                updateMessageStatus(message.getId(), "PROCESSED", message.getAttempts());

            } catch (Exception e) {
                System.err.println("Ошибка отправки трансфер-уведомления " + message.getId() + ": " + e.getMessage());

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
