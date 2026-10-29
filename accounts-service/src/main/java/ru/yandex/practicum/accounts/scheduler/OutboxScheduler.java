package ru.yandex.practicum.accounts.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.accounts.dto.EventEnvelope;
import ru.yandex.practicum.accounts.entity.OutboxMessage;
import ru.yandex.practicum.accounts.repository.OutboxRepository;
import ru.yandex.practicum.accounts.service.OutboxStatusService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class OutboxScheduler {

    public static final int TIME_INTERVAL = 15;
    public static final int DAY_INTERVAL = 1440;
    private final OutboxRepository outboxRepository;
    private final OutboxStatusService outboxStatusService;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.notification:bank-notifications}")
    private String notificationTopic;

    public OutboxScheduler(
            OutboxRepository outboxRepository,
            OutboxStatusService outboxStatusService,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {

        this.outboxRepository = outboxRepository;
        this.outboxStatusService = outboxStatusService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }


    @Scheduled(fixedDelay = 5000)
    public void processOutboxMessages() {

        log.debug("Сканирование таблицы Outbox на наличие сообщений в статусе PENDING...");

        List<OutboxMessage> pendingMessages =  outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        LocalDateTime now = LocalDateTime.now();

        if (!pendingMessages.isEmpty()) {
            log.info("Обнаружено {} транзакционных событий в статусе PENDING для отправки.", pendingMessages.size());
        }

        for (OutboxMessage message : pendingMessages) {
            long minutesAge = Duration.between(message.getCreatedAt(), now).toMinutes();

            if (minutesAge > TIME_INTERVAL && minutesAge < DAY_INTERVAL) {
                if (ThreadLocalRandom.current().nextDouble() > 0.30) {
                    log.debug("Сообщение {} остывает (возраст {} мин). Пропуск 70% цикла.", message.getId(), minutesAge);
                    continue; // Пропускаем 70% циклов для остывающих PENDING сообщений
                }
            } else if (minutesAge >= DAY_INTERVAL) {
                if (ThreadLocalRandom.current().nextDouble() > 0.02) {
                    log.debug("Сообщение {} устарело (старше суток). Пропуск 98% цикла.", message.getId());
                    continue; // Пропускаем 98% циклов для сообщений старше суток
                }
            }

            try {

                log.debug("Подготовка к публикации Outbox-сообщения: ID={}, Тип={}", message.getId(), message.getEventType());

                EventEnvelope envelope = new EventEnvelope(
                        message.getId(),
                        message.getEventType(),
                        message.getAggregateType(),
                        message.getPayload(),
                        Instant.now()
                );

                String jsonPayload = objectMapper.writeValueAsString(envelope);

                kafkaTemplate.send(notificationTopic, jsonPayload).get();

                outboxStatusService.updateStatus(message.getId(), "PROCESSED");

                log.info("Событие Outbox успешно доставлено в Kafka топик '{}'. ID сообщения: {}, Тип: {}",
                        notificationTopic, message.getId(), message.getEventType());

            } catch (JsonProcessingException e) {
                log.warn("Ошибка JSON-сериализации outbox сообщения {}: {}. Статус изменен на FAILED",
                        message.getId(), e.getMessage());

                outboxStatusService.updateStatus(message.getId(), "FAILED");
            }
            catch(Exception e) {
                log.error("Критический сбой при доставке outbox сообщения {} в Kafka брокер: {}",
                        message.getId(), e.getMessage(), e);
            }
        }
    }


}
