package ru.yandex.practicum.accounts.exception;

public class NotificationServiceUnavailableException extends RuntimeException {
    public NotificationServiceUnavailableException(Throwable cause) {
        super("Сервис уведомлений временно недоступен. Причина: " + cause.getMessage(), cause);
    }
}
