package ru.yandex.practicum.notification.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.notification.dto.EventEnvelope;
import ru.yandex.practicum.notification.handler.EventHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Component
public class NotificationKafkaListener {

    private final List<EventHandler> handlers;
    private final ObjectMapper objectMapper;
    private final Map<String, EventHandler> handlerMap = new HashMap<>();
    private final MeterRegistry meterRegistry;

    public NotificationKafkaListener(List<EventHandler> handlers, ObjectMapper objectMapper,
                                     MeterRegistry meterRegistry) {
        this.handlers = handlers;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        log.info("Инициализация обработчиков событий Kafka в сервисе нотификаций...");
        for (EventHandler handler : handlers) {
            handlerMap.put(handler.getSupportedEventType(), handler);
        }

        log.info("Успешно зарегистрировано {} обработчиков событий Kafka.", handlerMap.size());
    }

    @KafkaListener(
            topics = "${app.kafka.topics.notification:bank-notifications}",
            groupId = "notification-group-v3",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleEvent(String messageJson) {

        log.debug("Kafka консьюмер принял сырое событие из топика.");

        try {
            EventEnvelope envelope = objectMapper.readValue(messageJson, EventEnvelope.class);

            log.info("Успешно десериализован конверт события Outbox. ID: {}, Тип события: {}, Агрегат: {}",
                    envelope.eventId(), envelope.eventType(), envelope.aggregateType());

            EventHandler handler = handlerMap.get(envelope.eventType());

            if (handler != null) {
                log.debug("Передача конверта {} специализированному обработчику {}...",
                        envelope.eventId(), handler.getClass().getSimpleName());
                handler.handle(envelope);
                log.info("Событие {} успешно обработано сервисом уведомлений.", envelope.eventId());
            }
            else {
                log.warn("Внимание! Для типа события '{}' (Конверт ID: {}) не найдено подходящего обработчика в системе!",
                        envelope.eventType(), envelope.eventId());
            }

        } catch (Exception e) {
            log.error("КРИТИЧЕСКИЙ СБОЙ ПРИ ОБРАБОТКЕ СОБЫТИЯ В KAFKA LISTENER!" +
                            " Запуск парсинга имени пользователя для инкремента метрик. Ошибка: {}",
                    e.getMessage(), e);


            String username = "unknown";
            try {

                JsonNode rootNode = objectMapper.readTree(messageJson);

                JsonNode payloadNode = rootNode.path("payload");

                if (!payloadNode.isMissingNode()) {
                    if (payloadNode.isTextual()) {
                        JsonNode innerNode = objectMapper.readTree(payloadNode.asText());
                        username = innerNode.path("username").asText("unknown");
                    } else {
                        username = payloadNode.path("username").asText("unknown");
                    }
                }
            } catch (Exception jsonEx) {
                log.warn("Не удалось извлечь поле 'username'" +
                        " из поврежденного payload сообщения Kafka для записи метрик: {}", jsonEx.getMessage());
                username = "invalid_json_format";
            }

            Counter.builder("bank.notification.failed.total")
                    .description("Количество сбоев обработки и отправки уведомлений")
                    .tag("username", username)
                    .register(meterRegistry)
                    .increment();

            log.debug("Метрика 'bank.notification.failed.total' успешно увеличена для пользователя: '{}'", username);
            throw new RuntimeException("Ошибка обработки события Kafka, откат оффсета", e);
        }
    }
}

