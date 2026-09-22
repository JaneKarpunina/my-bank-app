package ru.yandex.practicum.cash.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.notification:bank-notifications}")
    private String notificationTopic;

    public CashOutboxScheduler(CashOutboxRepository outboxRepository,
                               OutboxStatusService outboxStatusService,
                               KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.outboxStatusService = outboxStatusService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    public void processCashOutboxMessages() {

        List<CashOutboxMessage> pendingMessages = outboxRepository.findMessagesForProcessing();

        for (CashOutboxMessage message : pendingMessages) {
            try {

                EventEnvelope envelope = new EventEnvelope(
                        message.getId(),
                        message.getEventType(),
                        "CASH",
                        message.getPayload(),
                        Instant.now()
                );

                String jsonPayload = objectMapper.writeValueAsString(envelope);

                kafkaTemplate.send(notificationTopic, jsonPayload).get();

                outboxStatusService.updateStatus(message.getId(), "PROCESSED", message.getAttempts());

            } catch (Exception e) {

                int currentAttempts = message.getAttempts() + 1;
                String nextStatus = (currentAttempts >= 5) ? "FAILED" : "PENDING";

                outboxStatusService.updateStatus(message.getId(), nextStatus, currentAttempts);
            }
        }
    }

}

