package ru.yandex.practicum.cash.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.cash.dto.EventEnvelope;
import ru.yandex.practicum.cash.entity.CashOutboxMessage;
import ru.yandex.practicum.cash.repository.CashOutboxRepository;
import ru.yandex.practicum.cash.service.OutboxStatusService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class CashOutboxScheduler {

    public static final int TIME_INTERVAL = 15;
    public static final int DAY_INTERVAL = 1440;

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

        List<CashOutboxMessage> pendingMessages = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        LocalDateTime now = LocalDateTime.now();

        for (CashOutboxMessage message : pendingMessages) {

            long minutesAge = Duration.between(message.getCreatedAt(), now).toMinutes();

            if (minutesAge > TIME_INTERVAL && minutesAge < DAY_INTERVAL) {
                if (ThreadLocalRandom.current().nextDouble() > 0.30) {
                    continue; // Пропускаем 70% циклов для остывающих PENDING сообщений
                }
            } else if (minutesAge >= DAY_INTERVAL) {
                if (ThreadLocalRandom.current().nextDouble() > 0.02) {
                    continue; // Пропускаем 98% циклов для сообщений старше суток
                }
            }
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

                outboxStatusService.updateStatus(message.getId(), "PROCESSED");

            } catch (JsonProcessingException e) {
                System.err.println("Ошибка отправки outbox сообщения " + message.getId() + ": " + e.getMessage());

                outboxStatusService.updateStatus(message.getId(), "FAILED");
            }
            catch(Exception e) {
                System.err.println("Ошибка отправки outbox сообщения " + message.getId() + ": " + e.getMessage());
            }
        }
    }

}

