package ru.yandex.practicum.notification.handler;

import ru.yandex.practicum.notification.dto.EventEnvelope;

public interface EventHandler {
    // Метод возвращает тип события, которое этот класс умеет обрабатывать
    String getSupportedEventType();

    // Сама логика обработки
    void handle(EventEnvelope envelope) throws Exception;
}
