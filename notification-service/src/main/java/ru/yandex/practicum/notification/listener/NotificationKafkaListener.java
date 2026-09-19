package ru.yandex.practicum.notification.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
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

    public NotificationKafkaListener(List<EventHandler> handlers, ObjectMapper objectMapper) {
        this.handlers = handlers;
        this.objectMapper = objectMapper;
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
            throw new RuntimeException("Ошибка обработки события Kafka, откат оффсета", e);
        }
    }
}

