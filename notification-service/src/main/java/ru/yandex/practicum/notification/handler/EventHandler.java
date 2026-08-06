package ru.yandex.practicum.notification.handler;

import ru.yandex.practicum.notification.dto.EventEnvelope;

public interface EventHandler {

    String getSupportedEventType();

    void handle(EventEnvelope envelope) throws Exception;
}
