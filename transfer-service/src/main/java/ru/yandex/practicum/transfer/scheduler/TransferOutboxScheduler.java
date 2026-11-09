package ru.yandex.practicum.transfer.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.transfer.dto.EventEnvelope;
import ru.yandex.practicum.transfer.entity.TransferOutboxMessage;
import ru.yandex.practicum.transfer.repository.TransferOutboxRepository;
import ru.yandex.practicum.transfer.service.OutboxStatusService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class TransferOutboxScheduler {

    public static final int TIME_INTERVAL = 15;
    public static final int DAY_INTERVAL = 1440;

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

        log.debug("Сканирование таблицы Transfer Outbox на наличие записей в статусе PENDING...");

        List<TransferOutboxMessage> pendingMessages = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        LocalDateTime now = LocalDateTime.now();

        if (!pendingMessages.isEmpty()) {
            log.info("Обнаружено {} событий перевода в статусе PENDING для публикации в Kafka.", pendingMessages.size());
        }

        for (TransferOutboxMessage message : pendingMessages) {

            long minutesAge = Duration.between(message.getCreatedAt(), now).toMinutes();

            if (minutesAge > TIME_INTERVAL && minutesAge < DAY_INTERVAL) {
                if (ThreadLocalRandom.current().nextDouble() > 0.30) {
                    log.debug("Сообщение перевода {} остывает (возраст {} мин). Пропуск 70% цикла.",
                            message.getId(), minutesAge);
                    continue; // Пропускаем 70% циклов для остывающих PENDING сообщений
                }
            } else if (minutesAge >= DAY_INTERVAL) {
                if (ThreadLocalRandom.current().nextDouble() > 0.02) {
                    log.debug("Сообщение перевода {} устарело (старше суток). Пропуск 98% цикла.", message.getId());
                    continue; // Пропускаем 98% циклов для сообщений старше суток
                }
            }
            try {

                log.debug("Подготовка к асинхронной публикации события перевода:" +
                        " ID={}, Тип={}", message.getId(), message.getEventType());

                EventEnvelope envelope = new EventEnvelope(
                        message.getId(),
                        message.getEventType(),
                        "TRANSFER",
                        message.getPayload(),
                        Instant.now()
                );

                String jsonPayload = objectMapper.writeValueAsString(envelope);

                kafkaTemplate.send(notificationTopic, jsonPayload).get();

                outboxStatusService.updateStatus(message.getId(), "PROCESSED");

                log.info("Событие перевода Outbox успешно отправлено в Kafka топик '{}'. ID сообщения: {}, Тип: {}",
                        notificationTopic, message.getId(), message.getEventType());

            } catch (JsonProcessingException e) {
                log.warn("Ошибка JSON-сериализации сообщения перевода {}: {}. Статус изменен на FAILED.",
                        message.getId(), e.getMessage());

                outboxStatusService.updateStatus(message.getId(), "FAILED");
            }
            catch(Exception e) {
                log.error("Критический сбой доставки сообщения перевода {} в брокер Kafka: {}",
                        message.getId(), e.getMessage(), e);
            }
        }
    }

}
