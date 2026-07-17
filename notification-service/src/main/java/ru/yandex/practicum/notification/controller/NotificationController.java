package ru.yandex.practicum.notification.controller;

import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.notification.dto.EventEnvelope;
import ru.yandex.practicum.notification.handler.EventHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final List<EventHandler> handlers;
    private final Map<String, EventHandler> handlerMap = new HashMap<>();

    public NotificationController(List<EventHandler> handlers) {
        this.handlers = handlers;
    }

    // Инициализируем карту при старте приложения
    @PostConstruct
    public void init() {
        for (EventHandler handler : handlers) {
            handlerMap.put(handler.getSupportedEventType(), handler);
        }
    }

    @PostMapping("/events")
    public ResponseEntity<Void> handleEvent(@RequestBody EventEnvelope envelope) {

        EventHandler handler = handlerMap.get(envelope.eventType());

        if (handler != null) {
            try {
                handler.handle(envelope);
            } catch (Exception e) {
                return ResponseEntity.badRequest().build();
            }
        }

        return ResponseEntity.ok().build();
    }


}

