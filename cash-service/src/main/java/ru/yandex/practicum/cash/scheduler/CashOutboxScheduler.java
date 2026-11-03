package ru.yandex.practicum.cash.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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

        log.debug("Сканирование таблицы Cash Outbox на наличие записей в статусе PENDING...");

        List<CashOutboxMessage> pendingMessages = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        LocalDateTime now = LocalDateTime.now();

        if (!pendingMessages.isEmpty()) {
            log.info("Обнаружено {} кассовых событий в статусе PENDING для отправки в Kafka.", pendingMessages.size());
        }

        for (CashOutboxMessage message : pendingMessages) {

            long minutesAge = Duration.between(message.getCreatedAt(), now).toMinutes();

            if (minutesAge > TIME_INTERVAL && minutesAge < DAY_INTERVAL) {
                if (ThreadLocalRandom.current().nextDouble() > 0.30) {
                    log.debug("Кассовое сообщение {} остывает (возраст {} мин). Пропуск 70% цикла.", message.getId(), minutesAge);
                    continue; // Пропускаем 70% циклов для остывающих PENDING сообщений
                }
            } else if (minutesAge >= DAY_INTERVAL) {
                if (ThreadLocalRandom.current().nextDouble() > 0.02) {
                    log.debug("Кассовое сообщение {} устарело (старше суток). Пропуск 98% цикла.", message.getId());
                    continue; // Пропускаем 98% циклов для сообщений старше суток
                }
            }
            try {

                log.debug("Подготовка к отправке кассового события: ID={}, Тип={}",
                        message.getId(), message.getEventType());

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

                log.info("Кассовое событие Outbox успешно опубликовано в Kafka топик '{}'. ID сообщения: {}, Тип: {}",
                        notificationTopic, message.getId(), message.getEventType());

            } catch (JsonProcessingException e) {

                log.warn("Ошибка JSON-сериализации кассового сообщения {}: {}. Статус переведен в FAILED.",
                        message.getId(), e.getMessage());
                outboxStatusService.updateStatus(message.getId(), "FAILED");
            }
            catch(Exception e) {
                log.error("Критический сбой доставки кассового сообщения {} в брокер Kafka: {}",
                        message.getId(), e.getMessage(), e);
            }
        }
    }

}

