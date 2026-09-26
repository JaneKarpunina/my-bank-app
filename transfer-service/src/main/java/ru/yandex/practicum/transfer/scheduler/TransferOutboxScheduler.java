package ru.yandex.practicum.transfer.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.notification:bank-notifications}")
    private String notificationTopic;

    public TransferOutboxScheduler(TransferOutboxRepository outboxRepository,
                                   OutboxStatusService outboxStatusService,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.outboxStatusService = outboxStatusService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    public void processTransferOutboxMessages() {

        List<TransferOutboxMessage> pendingMessages = outboxRepository.findMessagesForProcessing();

        for (TransferOutboxMessage message : pendingMessages) {
            try {

                EventEnvelope envelope = new EventEnvelope(
                        message.getId(),
                        message.getEventType(),
                        "TRANSFER",
                        message.getPayload(),
                        Instant.now()
                );

                String jsonPayload = objectMapper.writeValueAsString(envelope);

                kafkaTemplate.send(notificationTopic, jsonPayload).get();

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
