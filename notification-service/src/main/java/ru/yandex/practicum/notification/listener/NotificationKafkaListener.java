package ru.yandex.practicum.notification.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.notification.dto.EventEnvelope;
import ru.yandex.practicum.notification.handler.EventHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        for (EventHandler handler : handlers) {
            handlerMap.put(handler.getSupportedEventType(), handler);
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.notification:bank-notifications}",
            groupId = "notification-group-v3",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleEvent(String messageJson) {

        try {
            EventEnvelope envelope = objectMapper.readValue(messageJson, EventEnvelope.class);

            EventHandler handler = handlerMap.get(envelope.eventType());

            if (handler != null) {
                handler.handle(envelope);
            }

        } catch (Exception e) {
            System.err.println("КРИТИЧЕСКАЯ ОШИБКА ОБРАБОТКИ В KAFKA LISTENER: " + e.getMessage());

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
                username = "invalid_json_format";
            }

            Counter.builder("bank.notification.failed.total")
                    .description("Количество сбоев обработки и отправки уведомлений")
                    .tag("username", username)
                    .register(meterRegistry)
                    .increment();
            throw new RuntimeException("Ошибка обработки события Kafka, откат оффсета", e);
        }
    }
}

